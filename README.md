Mock Location app
=================

Used with desktop app to "walk" your gps wherever you like

# Building apk

To build use gradle wrapper binary. It will download relevant gradle version and build the project.

> ./gradlew build

apk will be generated in MockLocation/build/outputs/apk/MockLocation-debug.apk (you have to use debug version)

# Using application

- In **Developer options** enable **Allow mock locations** option
- Install and run apk
- Enter you Mock ID (whatever you like)
- Hit **Run** button. It will run Mock Location service, so you can close app now
- Open http://fieldsofwar.co/location_provider/index.php in your web browser
- Enter same Mock ID as on Mock Location app
- Click on map to simulate your mobile device location
