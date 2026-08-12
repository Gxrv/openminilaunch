# F-Droid submission

`com.katoaapps.openminilaunch.yml` is the build metadata for the official
`fdroiddata` repository. It is kept here so the reviewed recipe is visible to
contributors, but F-Droid reads its production copy from `fdroiddata`.

To submit the app:

1. Fork and clone `https://gitlab.com/fdroid/fdroiddata`.
2. Create a branch named `com.katoaapps.openminilaunch` from `master`.
3. Copy `com.katoaapps.openminilaunch.yml` into the clone's `metadata/`
   directory.
4. If `fdroidserver` is available, run:

   ```shell
   fdroid readmeta
   fdroid rewritemeta com.katoaapps.openminilaunch
   fdroid checkupdates --allow-dirty com.katoaapps.openminilaunch
   fdroid lint com.katoaapps.openminilaunch
   fdroid build com.katoaapps.openminilaunch
   ```

5. Commit the metadata as `New App: com.katoaapps.openminilaunch`, push the
   branch to the fork, and open a merge request against `fdroid/fdroiddata`.

F-Droid builds the APK from the tagged source. Do not upload a locally signed
APK as part of the standard submission.
