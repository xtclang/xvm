import * as fs from 'node:fs/promises';
import * as path from 'node:path';
import * as vscode from 'vscode';
import { script, type Coverage } from './script';
import { describe, Tour } from './tour';
import { RECORDING, TourUi } from './ui';

/**
 * Records the tour: the extension's features, step by step, in VS Code, on the card game's project that the launcher
 * (runTour.ts) made, for the tour's video and slides, which the composer makes from the run's `tour.json` and frames.
 * The run's `results.json` lists the steps and checks that failed, and the tour fails when there is any, so it is also
 * a test of the extension on a realistic project. `XTC_TOUR_SECTIONS` records only the sections it names.
 */
export async function run(): Promise<void> {
    const directory = process.env.XTC_TOUR_REPORT_DIR;
    const fixture = process.env.XTC_TOUR_FIXTURE;
    const project = vscode.workspace.workspaceFolders?.[0]?.uri.fsPath;
    if (!directory || !fixture || !project) throw new Error('Launch the tour with npm run test:tour, or -PvscodeTour=true');
    const sections = new Set((process.env.XTC_TOUR_SECTIONS ?? '').split(',').map(section => section.trim()).filter(Boolean));
    const ui = await TourUi.connect();
    const tour = new Tour(ui, project, fixture, directory, sections);
    const coverage: Coverage = {};
    try {
        await script(tour, coverage);
    } catch (failure) {
        await fs.writeFile(path.join(directory, 'failure.txt'), `${describe(failure)}\n${(failure as Error)?.stack ?? ''}\n`);
        throw failure;
    } finally {
        // a recorded run's screen, which the container records into screen.mp4 and copies into the run's directory,
        // from the time its start file holds; the composer cuts each step's clip from it, by the steps' times
        const recorded = process.env.XTC_RECORDING_START_FILE;
        const started = recorded ? Number((await fs.readFile(recorded, 'utf8').catch(() => '')).trim()) : NaN;
        const screen = RECORDING && Number.isFinite(started) ? { file: 'screen.mp4', start: started, frame: ui.frame() } : undefined;
        // the editor names the composer's video and deck, tour-vscode.mp4 and .pptx
        await fs.writeFile(path.join(directory, 'tour.json'), JSON.stringify({
            editor: 'vscode', project, ...(screen && { screen }), steps: tour.steps, checks: tour.checks, actions: ui.actions,
        }, null, 2) + '\n');
        const extension = vscode.extensions.all.find(item => item.packageJSON.name === 'xtc-language');
        await fs.writeFile(path.join(directory, 'results.json'), JSON.stringify({
            mode: 'TOUR',
            vscode: vscode.version,
            extension: extension?.packageJSON.version,
            commit: process.env.XTC_COMMIT,
            sections: [...sections],
            steps: tour.steps.length,
            frames: tour.steps.filter(step => step.image).length,
            checks: tour.checks.length,
            failed: tour.failures,
            // the steps and checks not run, as what they need was missing after an earlier failure, apart from it
            skippedCount: tour.skipped.length,
            skipped: tour.skipped,
            checksRun: tour.checks,
            coverage,
        }, null, 2) + '\n');
        tour.dispose();
        await ui.close();
        console.log(`[tour] ${tour.steps.length} steps, ${tour.checks.length} checks, ${tour.failures.length} failed, ${tour.skipped.length} skipped: ${path.join(directory, 'tour.json')}`);
    }
    if (tour.failures.length) {
        throw new Error(`${tour.failures.length} of the tour's steps and checks failed:\n${tour.failures.join('\n')}`);
    }
}
