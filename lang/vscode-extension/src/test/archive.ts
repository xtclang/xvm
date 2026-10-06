import { Entry, fromBuffer } from 'yauzl';

/** Read the actual ZIP contents in both extension and shared playbook acceptance tests. */
export function readArchive(bytes: Buffer): Promise<Map<string, Buffer>> {
    return new Promise((resolve, reject) => fromBuffer(bytes, { lazyEntries: true }, (error, zip) => {
        if (error) { reject(error); return; }
        const entries = new Map<string, Buffer>();
        zip.on('error', reject).on('end', () => resolve(entries));
        zip.on('entry', (entry: Entry) => zip.openReadStream(entry, (failure, stream) => {
            if (failure) { zip.close(); reject(failure); return; }
            const chunks: Buffer[] = [];
            stream.on('error', reject).on('data', chunk => chunks.push(chunk)).on('end', () => {
                entries.set(entry.fileName, Buffer.concat(chunks));
                zip.readEntry();
            });
        }));
        zip.readEntry();
    }));
}
