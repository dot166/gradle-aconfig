# libaconfig

this is 2 'small' java library's (1 android, and one java, both are written in java) for read/write aconfig editing, since normally aconfig values are edited using adb so i just decided to do it another way.

the library that corresponds to the type of project is automatically downloaded from this folder by the gradle-aconfig plugin to a temporary folder, then its sources imported into the project.

i am still working on the writable config file for non android (openjdk) projects.