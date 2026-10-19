# Project-specific R8 rules.
#
# R8 full mode (the AGP default) only keeps a class's constructor when a rule names it.
# Some libraries' rules keep just the class, which breaks code that creates it by name.

# Glance creates widget click actions by class name (TapAction, ResetAction).
-keep class * implements androidx.glance.appwidget.action.ActionCallback { <init>(); }

# Room creates WorkManager's database by name. Room 2.7+ ships this rule itself;
# kept here in case an older Room is resolved again (see app/build.gradle.kts).
-keep class * extends androidx.room.RoomDatabase { <init>(); }
