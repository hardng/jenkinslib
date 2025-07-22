package io.jenkins.agent

abstract class AgentInterface implements Serializable {
    // 抽象方法，子类必须实现
    abstract void build()
    abstract void buildImage()
    abstract void deploy()
}
