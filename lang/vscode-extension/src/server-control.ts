import * as fs from 'node:fs/promises';
import * as http from 'node:http';
import * as path from 'node:path';
import { randomUUID, timingSafeEqual } from 'node:crypto';

/** Editor-owned CLI endpoint: survives server crashes and delegates restart to the existing client lifecycle. */
export class ServerControlEndpoint {
    private readonly id = randomUUID();
    private readonly token = randomUUID() + randomUUID();
    private readonly server: http.Server;
    private readonly descriptor: string;
    private busy = false;
    private closed = false;

    private constructor(directory: string, private readonly projects: () => string[], private readonly restart: () => Promise<void>) {
        this.descriptor = path.join(directory, `${this.id}.json`);
        this.server = http.createServer((request, response) => {
            void this.handle(request, response).catch(() => response.destroy());
        });
        this.server.requestTimeout = 5000;
        this.server.headersTimeout = 5000;
    }

    static async start(directory: string, projects: () => string[], restart: () => Promise<void>): Promise<ServerControlEndpoint> {
        await fs.mkdir(directory, { recursive: true, mode: 0o700 });
        if ((await fs.lstat(directory)).isSymbolicLink()) throw new Error('Control directory must not be a symbolic link');
        if (process.platform !== 'win32') await fs.chmod(directory, 0o700);
        const control = new ServerControlEndpoint(directory, projects, restart);
        try {
            await new Promise<void>((resolve, reject) => {
                control.server.once('error', reject);
                control.server.listen(0, '127.0.0.1', resolve);
            });
            const address = control.server.address();
            if (!address || typeof address === 'string') throw new Error('Control server has no TCP port');
            await fs.writeFile(control.descriptor, JSON.stringify({ ...control.status(), port: address.port, token: control.token }),
                { flag: 'wx', mode: 0o600 });
            return control;
        } catch (error) {
            await control.close();
            throw error;
        }
    }

    private status(): object {
        return { version: 1, id: this.id, ide: 'vscode', pid: process.pid, projects: this.projects() };
    }

    private async handle(request: http.IncomingMessage, response: http.ServerResponse): Promise<void> {
        const actual = Buffer.from(request.headers.authorization ?? '');
        const expected = Buffer.from(`Bearer ${this.token}`);
        const reply = (code: number, value: object) => {
            response.writeHead(code, { 'Content-Type': 'application/json' });
            response.end(JSON.stringify(value));
        };
        if (request.headers.origin !== undefined || actual.length !== expected.length || !timingSafeEqual(actual, expected)) {
            reply(403, { error: 'Unauthorized' });
            return;
        }
        if (request.method === 'GET' && request.url === '/status') {
            reply(200, this.status());
        } else if (request.method === 'POST' && request.url === '/restart') {
            if (this.busy) { reply(409, { error: 'Restart already in progress' }); return; }
            this.busy = true;
            try {
                if (this.closed) throw new Error('Workspace is closing');
                await this.restart();
                reply(200, { ...this.status(), message: 'Server restarted; the editor is resynchronizing open buffers.' });
            } catch (error) {
                reply(500, { error: error instanceof Error ? error.message : String(error) });
            } finally { this.busy = false; }
        } else {
            reply(404, { error: 'Unknown operation' });
        }
    }

    async close(): Promise<void> {
        if (this.closed) return;
        this.closed = true;
        this.server.closeAllConnections();
        await new Promise<void>(resolve => this.server.close(() => resolve()));
        await fs.rm(this.descriptor, { force: true });
    }
}
