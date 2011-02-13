==================================================
Ocean Observatories Initiative Cyberinfrastructure
Integrated Observatory Network (ION)
ooi-netcdf-java - OOI Version of the NetCDF-Java Library
==================================================

WARNING - this is a large repository and will take some time to download

Get ooi-netcdf-java with
::
	git clone git@github.com:ooici-eoi/ooici-netcdf-java.git
	cd ooi-netcdf-java
	git checkout develop


Dependencies
============

The project includes all required dependencies.  It uses functionality from
two other OOICI projects (the compiled jars are included in latest_stable/ooici/lib).
These projects are:
	ioncore-java - https://github.com/ooici-eoi/ioncore-java
	ion-object-definitions - https://github.com/ooici/ion-object-definitions

Compile
=======
To compile the library and create the jar file:
::
	ant makeFullJar
	
Use
===
1. make the ooici-netcdf-full-4.2.4.jar
2. modify the ooici-conn.properties file to have the appropriate connection information as necessary
3. place the ooici-conn.properties file and compiled jar file together
4. run one the following commands from the directory containing the jar and properties files

To print information about the resource to the console:
::
	java -cp ooi-netcdf-full-4.2.4.jar ucar.nc2.NCdumpW ooici:<resid> <opts>

where:
	<resid> : is the OOICI resource ID for a given dataset

available <opts> are:
   * cdl : output format is CDL
   * ncml : output format is NcML
   * -vall : dump all variable data
   * -c : dump coordinate variable data
   * -v varName1;varName2; : dump specified variable(s)
   * -v varName(0:1,:,12) : dump specified variable section
   * Default is to dump the header info only.

To write the resource to disk as a NetCDF File:
::
	java -cp ooi-netcdf-full-4.2.4.jar ucar.nc2.FileWriter -in ooici:<resid> -out <outfile>

where:
	<resid> : is the OOICI resource ID for a given dataset
	<outfile> : is the path &/or name of the output file