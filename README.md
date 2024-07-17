# response time analysis tool

## 介绍
在华为胡杨林项目的支持下，中山大学计算机学院RTS实验室研发了一款面向混合关键实时系统的最坏响应时间分析工具，
该工具考虑了混合关键系统的全运行场景，并支持多种主流资源共享协议的响应时间分析，为混合关键系统提供实时性验证工具。
混合关键实时系统的最坏响应时间分析工具涵盖三个主要功能模块，分别为系统生成模块、系统分析模块、批量测试模块，
支持用户在不同的系统配置下对主流资源共享协议进行可视化的可调度性分析，有助于帮助用户测试、比较和开发新的资源共享协议。


## 技术栈
混合关键系统响应时间分析工具采用了MVC（Model-View-Controller）作为软件架构的设计模式，基于Java和JavaFX开源平台开发，对应版本如下：

- `Java  20.0.2`
- `JavaFX 18`


## 代码运行指南

- 从代码仓库下载代码至本地。
- 使用IDEA打开 `resposon-time-analysis-tool` 项目
- IDEA会自动根据pom.xml下载项目所需的依赖项
- 找到主入口类为`com.demo.tool.Tool`。使用IDEA的运行按钮启动项目。
  

## 代码文件夹说明

源文件位于`resposon-time-analysis-tool/src/main/java/com/demo/tool`文件夹下，其具体内容如下：
- `responstimeanalysis/analysis`: 响应时间分析实现
- `responstimeanalysis/generator`: 系统生成，任务分配，优先级分配等实现
- `Analysis.java`：实现工具所需的主要逻辑功能。该类包含了三个关键函数，分别对应于该工具的三个主要功能模块。
- `Controller.java`：工具的控制器实现，负责协调视图层和模型层之间的交互。

## 工具使用教程

找到`response-time-analysis-tool\target\build-link-package`文件夹下`response-time-analysis-tool-1.0.exe`安装程序，下载并点击安装即可。



## 工具打包方式

- 调用`javafx`的maven插件，使用`jlink`进行打包
  - `mvn javafx:jlink`
- 在项目根目录下使用该命令
  - `jpackage --type app-image -n response-time-analysis-tool -m "com.example.demo/com.demo.tool.Tool" --icon ".\src\main\resources\icon\logo.ico" --runtime-image ".\target\app" --dest ".\target\build-package"`
- 最后，执行如下命令打包出可安装程序.exe
  - `jpackage.exe -n response-time-analysis-tool --app-image .\target\build-package\response-time-analysis-tool  --app-version 1.0 --dest .\target\build-link-package --temp .\target\build-link-package\temp --win-dir-chooser --win-menu --win-menu-group response-time-analysis-tool  --win-shortcut`

