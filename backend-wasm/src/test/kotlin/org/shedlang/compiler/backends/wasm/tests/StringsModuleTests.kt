package org.shedlang.compiler.backends.wasm.tests

import org.shedlang.compiler.backends.tests.StackStringsModuleTests

class StringsModuleTests: StackStringsModuleTests(environment = WasmCompilerExecutionEnvironment)
