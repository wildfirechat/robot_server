# 野火IM机器人应用
作为野火IM机器人应用的演示，本工程仅演示机器人应用的接口。

#### 编译
```
mvn package
```

#### 修改配置
本演示服务有2个配置文件在工程的```config```目录下，分别是```application.properties```和```robot.properties```。请正确配置放到jar包所在的目录下的```config```目录下。

#### 运行
在```target```目录找到```robot-XXXX.jar```，把jar包和放置配置文件的```config```目录放到一起，然后执行下面命令：
```
java -jar robot-XXXXX.jar
```

#### 使用到的开源代码
1. [TypeBuilder](https://github.com/ikidou/TypeBuilder) 一个用于生成泛型的简易Builder

#### LICENSE
UNDER MIT LICENSE. 详情见LICENSE文件
