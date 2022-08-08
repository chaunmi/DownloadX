package zlc.season.downloadx

sealed class State() {
    class None : State()
    class Waiting : State()
    class Downloading : State()
    class Stopped : State()
    class Failed(var throwable: Throwable? = null) : State()
    class Succeed : State()
}