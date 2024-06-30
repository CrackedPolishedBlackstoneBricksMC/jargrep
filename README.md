# jargrep

Search a collection of jar files.

For all files, searches filenames and file contents. For class files, searches field and method names, field values, and ldc constants.

## Download

[Go to the releases tab.](https://github.com/quat1024/jargrep/releases).

## Compilation

`./gradlew fat`

The built fatjar will be in `./build/libs`.

## Installation

* Install Java 8 or later.
* Download `jargrep-(version)-all.jar` and put it somewhere on your system. You can rename it.
* If you want to install it system-wide, set up a shell alias:
  * `alias jargrep="java -jar /path/to/jargrep.jar"`

## Usage

`java -jar jargrep.jar [options] pattern [files...]?`

See `jargrep --help`. The syntax is kinda similar to `grep -e`, although of course fewer things are supported. 

If you don't specify any files, all jars/zips/classes in the current directory will be searched. (Might include jargrep itself!) The pattern is parsed as a standard java regex with `Pattern.compile`. 
