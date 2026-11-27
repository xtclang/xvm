import * as assert from 'node:assert';
import { createServer } from 'node:net';

/** Let the OS allocate a loopback port; fail if another process wins the short launch race. */
export async function workbenchPort(): Promise<number> {
    const server = createServer();
    await new Promise<void>((resolve, reject) => { server.once('error', reject); server.listen(0, '127.0.0.1', resolve); });
    const address = server.address();
    assert.ok(address && typeof address !== 'string');
    await new Promise<void>((resolve, reject) => server.close(error => error ? reject(error) : resolve()));
    return address.port;
}
