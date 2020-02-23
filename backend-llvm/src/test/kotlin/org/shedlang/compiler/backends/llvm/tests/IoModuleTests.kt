package org.shedlang.compiler.backends.llvm.tests

import org.shedlang.compiler.backends.tests.StackIoModuleTests

class IoModuleTests: StackIoModuleTests(environment = LlvmCompilerExecutionEnvironment)
