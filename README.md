# jargrep

A recursive Java archive searching tool. Searches inside `.zip`s and `.jar`s, and parses `.class`es for richer information.

```console
$ java -jar jargrep.jar "Mapper function"
archive sodium-fabric-0.5.8+mc1.20.1.jar
-> archive META-INF/jars/fabric-api-base-0.4.30+7abfd51577.jar
  |-> class net/fabricmc/fabric/api/util/TriState.class
  |  |-> method map
  |  |  |-> ldc: Mapper function cannot be null
```

## Download

[Go to the releases tab.](https://github.com/quat1024/jargrep/releases)

## Installation

* Install Java 8 or later.
* [Download `jargrep-`*`(version)`*`-all.jar`](https://github.com/quat1024/jargrep/releases) and put it somewhere on your system. You can rename it.
* Then run `java -jar /path/to/jargrep.jar ` *`[arguments...]`*

<details><summary>Advanced</summary>

If you want to install it system-wide, or you're sick of typing `java -jar`, set up a shell alias. Try `alias jargrep="java -jar /path/to/jargrep.jar"`.

</details>

## Usage

`java -jar jargrep.jar [options...] pattern [files...]?`

For example, to search for the pattern 'needle' inside 'haystack.jar', try

    jargrep "needle" haystack.jar

If you don't specify any files to search, jargrep will search all .jar, .zip, and .class files in the current directory (including itself!)

## Compilation

Run `./gradlew fat`. The built `-all` jar will reside in `./build/libs`.