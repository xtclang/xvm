import * as assert from 'node:assert';
import { failureLine, LineSplitter, parseServiceMessage, selectorOf } from '../../test-messages';

suite('Test service messages', () => {
    test('a TeamCity message is its name and its attributes, unescaped', () => {
        const message = parseServiceMessage(
            '##teamcity[testFailed name=\'Mathy.fails()\' nodeId=\'Mathy:void fails()\' parentNodeId=\'Mathy:\' ' +
            'locationHint=\'xunit:fails\' message=\'it|\'s |[not|] three\' details=\'IllegalState: no|n\tat fails() (Mathy.x:10)|n||\']');
        assert.ok(message);
        assert.strictEqual(message.name, 'testFailed');
        assert.deepStrictEqual(message.attributes, {
            name: 'Mathy.fails()',
            nodeId: 'Mathy:void fails()',
            parentNodeId: 'Mathy:',
            locationHint: 'xunit:fails',
            message: 'it\'s [not] three',
            details: 'IllegalState: no\n\tat fails() (Mathy.x:10)\n|',
        });
        assert.strictEqual(selectorOf(message), 'fails');
    });

    test('other output is no message, and a message without an xunit hint has no selector', () => {
        assert.strictEqual(parseServiceMessage('Started:   Mathy.adds()'), undefined);
        assert.strictEqual(parseServiceMessage('##teamcity[testStarted name=\'x\''), undefined);
        const suiteStart = parseServiceMessage('  ##teamcity[testSuiteStarted name=\'Mathy\' locationHint=\'xunit:\']  ');
        assert.strictEqual(suiteStart?.name, 'testSuiteStarted');
        assert.strictEqual(selectorOf(suiteStart!), '');
        assert.strictEqual(selectorOf(parseServiceMessage('##teamcity[testStarted name=\'x\']')!), undefined);
    });

    test('a failure is placed at the line its stack names in the test\'s own file', () => {
        const details = 'IllegalState: boom\n\tat helper() (Other.x:3)\n\tat fails() (Mathy.x:10)\n\tat executor.invoke() (executor.x:30)';
        assert.strictEqual(failureLine(details, 'Mathy.x'), 10);
        assert.strictEqual(failureLine(details, 'Other.x'), 3);
        assert.strictEqual(failureLine(details, 'Absent.x'), undefined);
    });

    test('output that arrives in pieces is split into whole lines', () => {
        const lines = new LineSplitter();
        assert.deepStrictEqual(lines.feed('##teamcity[testStarted na'), []);
        assert.deepStrictEqual(lines.feed('me=\'a\']\r\ninside a\nlast'), ['##teamcity[testStarted name=\'a\']', 'inside a']);
        assert.deepStrictEqual(lines.flush(), ['last']);
        assert.deepStrictEqual(lines.flush(), []);
    });
});
