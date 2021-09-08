/*
 * Copyright (c) Patrick Lang in collaboration with bitfire web engineering.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.techbee.jtx

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.activityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
@LargeTest
class EspressoTestcase {

        /**
         * Use [androidx.test.ext.junit.rules.ActivityScenarioRule] to create and launch the activity under test before each test,
         * and close it after each test. This is a replacement for
         * [androidx.test.rule.ActivityTestRule].
         */
        @get:Rule var activityScenarioRule = activityScenarioRule<MainActivity>()

        @Test
        fun goToDate_only_visible_for_Journals() {

            // Type text and then press the button.
            onView(withText(R.string.list_tabitem_todos)).perform(click())
            onView(withId(R.id.menu_list_gotodate)).check(doesNotExist())

            onView(withText(R.string.list_tabitem_notes)).perform(click())
            onView(withId(R.id.menu_list_gotodate)).check(doesNotExist())
            //onView(withId(R.id.todos_tab)).perform(click())
        }


    @Test
    fun use_fab_to_create_new_journal() {

        onView(withText(R.string.list_tabitem_journals)).perform(click())
        onView(withId(R.id.fab)).perform(click())
        onView(withId(R.id.edit_summary_edit_textinputfield)).perform(typeText("Espresso Journal"))
        onView(withId(R.id.edit_fab_save)).perform(click())
        onView(withText("Espresso Journal"))
    }

    @Test
    fun use_fab_to_create_new_note() {

        onView(withText(R.string.list_tabitem_notes)).perform(click())
        onView(withId(R.id.fab)).perform(click())
        onView(withId(R.id.edit_summary_edit_textinputfield)).perform(typeText("Espresso Note"))
        onView(withId(R.id.edit_fab_save)).perform(click())
        onView(withText("Espresso Note"))
    }

    @Test
    fun use_fab_to_create_new_todo() {

        onView(withText(R.string.list_tabitem_todos)).perform(click())
        onView(withId(R.id.fab)).perform(click())
        onView(withId(R.id.edit_summary_edit_textinputfield)).perform(typeText("Espresso Todo"))
        onView(withId(R.id.edit_fab_save)).perform(click())
        onView(withText("Espresso Todo"))
    }


    @Test
    fun open_about_click_through_tabs() {

        onView(withContentDescription("Open navigation drawer")).perform(click())
        onView(withId(R.id.nav_about)).perform(click())
        onView(withContentDescription(R.string.about_tabitem_translations)).perform(click())
        onView(withContentDescription(R.string.about_tabitem_libraries)).perform(click())
        onView(withContentDescription(R.string.about_tabitem_jtx)).perform(click())

        onView(withId(R.id.about_app_icon))
    }

    @Test
    fun open_settings_menu() {

        onView(withContentDescription("Open navigation drawer")).perform(click())
        onView(withId(R.id.nav_app_settings)).perform(click())
        onView(withText(R.string.settings_enforce_dark_theme))

    }

}