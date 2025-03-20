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

### Complete option listing

|                               Opt | Default | Desc                                                                                |
|----------------------------------:|:-------:|:------------------------------------------------------------------------------------|
|                      `-?, --help` |         | Print this help message.                                                            |
|               `-V, -v, --version` |         | Print version.                                                                      |
|              `-F, --fixed-string` |         | Enable Pattern.LITERAL mode, searching for the string verbatim.                     |
|          `-i, --case-insensitive` |         | Enable case-insensitive mode.                                                       |
|             `--include <pattern>` |         | When searching archives, only look in files matching this pattern.                  |
|             `--exclude <pattern>` |         | When searching archives, don't look in files matching this pattern.                 |
|    `--search-filenames [boolean]` |  true   | Report matches in the names of files.                                               |
|         `--search-text [boolean]` |  true   | Report matches inside text files.                                                   |
|     `--search-binaries [boolean]` |  true   | Report matches inside binary files.                                                 |
|     `--search-archives [boolean]` |  true   | Report matches inside nested archives.                                              |
|      `--search-classes [boolean]` |  true   | Report matches inside class files.                                                  |
|       `--search-fields [boolean]` |  true   | Report matches inside class field names.                                            |
| `--search-field-values [boolean]` |  true   | Report matches inside some(!) final fields.                                         |
|      `--search-methods [boolean]` |  true   | Report matches inside class method names.                                           |
|         `--search-ldcs [boolean]` |  true   | Report matches in LDC constants inside methods.                                     |
|     `--alwaysRawSearch [boolean]` |  false  | Also perform a raw search over binaries even if they can be parsed as classes/zips. |


## Compilation

~~Run `./gradlew fat`. The built `-all` jar will reside in `./build/libs`.~~ ive had enough of gradle lol

Run `./mill JargrepCli.assembly`. The built fatjar will reside in `./out/JargrepCli/assembly.dest/out.jar`.

Test with `./mill __.test` (two underscores).