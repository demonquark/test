test
====

projects used to try out new stuff


Notes
====

----- ./test project -----

com.kris.test.camera uses tesseract 

-	Download and build the tess-two project from https://github.com/rmtheis/tess-two
-	Build the tess-two project using the instructions from https://github.com/rmtheis/tess-two

    Quick reminder of those instructions:

```
cd <project-directory>/tess-two
ndk-build
android update project --path .
ant release
```

-	Import both the tess-two project into eclipse
-	Add tess-two as a library project: Right click -> Properties -> Android -> Library -> Add
