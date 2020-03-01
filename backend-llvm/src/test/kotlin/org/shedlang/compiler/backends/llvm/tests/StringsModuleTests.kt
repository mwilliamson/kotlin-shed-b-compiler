package org.shedlang.compiler.backends.llvm.tests

import org.shedlang.compiler.backends.tests.StackStringsModuleTests

class StringsModuleTests: StackStringsModuleTests(environment = LlvmCompilerExecutionEnvironment)
