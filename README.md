## its not finished yet

i wrote this in about 30 minutes the code is crap.

# jargrep

Search a collection of jar files.

For all files, searches filenames and file contents. For class files, searches field and method names, field values, and ldc constants.

## Usage

`java -jar jargrep.jar -- [pattern] [files...]?`

`pattern` will be parsed as a typical Java regex (most shells will require quoting it.) If you don't pass any files all jars in the cwd will be used (including jargrep itself? lol)

currently `files` must be jar files, want to expand it to class files too 