package org.shedlang.compiler.backends.llvm.tests

import org.shedlang.compiler.backends.tests.StackIntToStringModuleTests

class IntToStringModuleTests: StackIntToStringModuleTests(environment = LlvmCompilerExecutionEnvironment)
