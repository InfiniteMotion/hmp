@echo off
@rem ##########################################################################
@rem  HMP 低内存构建包装脚本 (Windows)
@rem
@rem  为什么需要：
@rem    本机 Android Studio 常驻占用大量内存，而 gradle.properties 里默认
@rem    org.gradle.jvmargs=-Xmx4096m + org.gradle.parallel=true，会导致
@rem    Gradle daemon 与 Kotlin daemon 合计超出可用内存，被操作系统静默杀死。
@rem    典型现象：构建日志停在 "Reusing configuration cache."，报
@rem    "gradle daemon disappeared unexpectedly"，且没有 error / hs_err 文件。
@rem
@rem    注意：jvmargs / parallel / workers.max 都是 Gradle **启动期**属性，
@rem    无法在 build.gradle.kts 里条件化覆盖（脚本执行时 daemon 早已按
@rem    gradle.properties 启动）。因此只能用包装脚本在命令行层面覆盖。
@rem
@rem  用法：
@rem    gradlew-lowmem.bat testCore
@rem    gradlew-lowmem.bat compileUi
@rem    gradlew-lowmem.bat testAll
@rem    gradlew-lowmem.bat tasks --group verification
@rem
@rem  说明：
@rem    - 透传所有参数给 gradlew.bat
@rem    - 附带 --no-daemon（低内存机器上不留驻 daemon，避免累积占用）
@rem    - 内存充足或需要速度时，请直接用 gradlew.bat
@rem
@rem  参数取值的实测依据（2026-09-15 在本机反复验证）：
@rem    Gradle daemon 堆 **1024m** 是本机唯一能连测试一起跑通的值。
@rem    用 1536m / 2048m 时，编译全部能过（都 UP-TO-DATE），但一旦进入
@rem    :shared:desktopTest —— 也就是要 fork 测试 JVM 的那一刻 ——
@rem    daemon 就会被 OS 杀掉（"daemon disappeared"，无 hs_err，daemon 日志
@rem    直接断掉，事件日志也无任何记录，属外部 kill）。
@rem    改小到 1024m 后 fork 成功，测试跑完 BUILD SUCCESSFUL。
@rem    另需 -Dkotlin.compiler.execution.strategy=in-process 避免再起一个
@rem    Kotlin daemon 抢内存。
@rem ##########################################################################

setlocal
set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.

@rem 低内存参数：daemon 堆 1024m（实测可 fork 测试 JVM），Kotlin 编译器走 in-process，关并行
call "%DIRNAME%gradlew.bat" %* ^
  --no-daemon --console=plain ^
  -Dorg.gradle.parallel=false ^
  -Dorg.gradle.workers.max=1 ^
  -Dorg.gradle.jvmargs="-Xmx1024m -XX:MaxMetaspaceSize=384m -Dfile.encoding=UTF-8" ^
  -Dkotlin.compiler.execution.strategy=in-process

endlocal
exit /b %ERRORLEVEL%
