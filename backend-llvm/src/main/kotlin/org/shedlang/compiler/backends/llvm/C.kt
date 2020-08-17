package org.shedlang.compiler.backends.llvm

internal class CHeader(private val includeGuardName: String, private val statements: List<CTopLevelStatement>) {
    fun serialise(): String {
        return "#ifndef $includeGuardName\n" +
            "#define $includeGuardName\n" +
            statements.map { statement -> statement.serialise() }.joinToString("") +
            "#endif\n"
    }
}

internal interface CTopLevelStatement {
    fun serialise(): String
}

internal class CVariableDeclaration(private val type: CType, private val name: String): CTopLevelStatement {
    override fun serialise(): String {
        return "extern ${type.serialise()} $name;\n"
    }
}

internal interface CType {
    fun serialise(): String
}

internal class CStruct(private val members: List<Pair<CType, String>>): CType {
    override fun serialise(): String {
        val membersString = members
            .map { (memberType, memberName) -> "\n    ${memberType.serialise()} $memberName;" }
            .joinToString("")

        return "struct {" + membersString + "\n}"
    }
}

internal class CNamedType(private val name: String): CType {
    override fun serialise(): String {
        return name
    }
}
