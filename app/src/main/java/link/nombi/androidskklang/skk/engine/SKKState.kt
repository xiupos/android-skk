package link.nombi.androidskklang.engine

interface SKKState {
    val isTransient: Boolean
    val icon: Int
    fun handleKanaKey(context: SKKEngine)
    fun processKey(context: SKKEngine, pcode: Int)
    fun afterBackspace(context: SKKEngine)
    fun handleCancel(context: SKKEngine): Boolean
}
