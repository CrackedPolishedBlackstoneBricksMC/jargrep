# jargrep

Search a collection of jar files.

For all files, searches filenames and file contents. For class files, searches field and method names, field values, and ldc constants.

## Compilation

`./gradlew fat`

The built fatjar will be in `./build/libs`.

## Installation

* Install Java 8 or later.
* Download `jargrep-(version)-all.jar` and put it somewhere on your system. You can rename it.
* Set up a shell alias:
  * `alias jargrep="java -jar /path/to/jargrep.jar"`

## Usage

`jargrep [options] pattern [files...]?`

`pattern` will be parsed with `Pattern.compile`. If you don't specify any files, all jars/zips/classes in the cwd will be searched. (Might include jargrep itself.) The syntax is otherwise kinda similar to `grep -e` although of course fewer things are supported. See `jargrep --help`.