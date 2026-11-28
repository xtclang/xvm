package org.xtclang.idea.playbook

/**
 * The JUnit tag of the tests that check the playbook's and the tour's harness, not the product: testPlaybookHarness runs
 * them, alone or first in every testCompilerPlaybook run, which leaves them out of its own; no `check` runs them.
 */
const val HARNESS = "harness"
