import * as assert from 'node:assert';
import * as fs from 'node:fs';
import * as os from 'node:os';
import * as path from 'node:path';
import { pathToFileURL } from 'node:url';
import { inheritedLibraries, normalizeLibraries } from '../../library-configuration';

suite('Compiler library configuration', () => {
    test('inheritance is distinct from explicitly empty libraries', () => {
        assert.deepStrictEqual(normalizeLibraries(undefined, []), inheritedLibraries);
        assert.deepStrictEqual(normalizeLibraries({ modulePath: [] }, []), { modulePath: [], sourceAttachments: [] });
    });

    test('order and attachment association survive normalization', () => {
        const base = 'file:///workspace/';
        assert.deepStrictEqual(normalizeLibraries({ modulePath: ['second', 'first'],
            sourceAttachments: [{ module: 'Library', roots: ['sources', 'fallback'] }] }, [base]), {
            modulePath: ['file:///workspace/second', 'file:///workspace/first'],
            sourceAttachments: [{ module: 'Library', roots: ['file:///workspace/sources', 'file:///workspace/fallback'] }]
        });
    });

    test('ambiguous roots aliases remote schemes and malformed attachments are rejected', () => {
        assert.throws(() => normalizeLibraries({ modulePath: ['relative'] }, ['file:///one/', 'file:///two/']), /exactly one/);
        assert.throws(() => normalizeLibraries({ modulePath: ['sources', './sources'] }, ['file:///workspace/']), /Duplicate/);
        assert.throws(() => normalizeLibraries({ modulePath: ['https://example.org/library.xtc'] }, []), /local file/);
        assert.throws(() => normalizeLibraries({ sourceAttachments: [{ module: 'Library', roots: [] }] }, []), /ordered roots/);
        assert.throws(() => normalizeLibraries({ sourceAttachments: [{ module: '', roots: [] }] }, []), /module name/);
        assert.throws(() => normalizeLibraries({ typo: [] }, []), /Unknown/);
    });

    test('settings application validates existing files before persistence', () => {
        const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'xtc-libraries-test-'));
        const uri = pathToFileURL(directory + path.sep).toString();
        try {
            fs.mkdirSync(path.join(directory, 'sources'));
            assert.throws(() => normalizeLibraries({ modulePath: ['missing.xtc'] }, [uri], true), /does not exist/);
            assert.ok(normalizeLibraries({ modulePath: ['sources'] }, [uri], true).modulePath?.length);
        } finally { fs.rmSync(directory, { recursive: true, force: true }); }
    });
});
