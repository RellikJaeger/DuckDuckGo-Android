/*
 * Copyright (c) 2017 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.app.browser

import android.arch.core.executor.testing.InstantTaskExecutorRule
import android.arch.lifecycle.Observer
import android.net.Uri
import com.duckduckgo.app.browser.BrowserViewModel.Command
import com.duckduckgo.app.browser.BrowserViewModel.Command.LandingPage
import com.duckduckgo.app.browser.BrowserViewModel.Command.Navigate
import com.duckduckgo.app.browser.omnibar.OmnibarEntryConverter
import com.duckduckgo.app.global.StringResolver
import com.duckduckgo.app.privacymonitor.model.PrivacyGrade
import com.duckduckgo.app.privacymonitor.store.PrivacyMonitorRepository
import com.duckduckgo.app.privacymonitor.store.TermsOfServiceStore
import com.duckduckgo.app.trackerdetection.model.TrackerNetworks
import com.duckduckgo.app.trackerdetection.model.TrackingEvent
import com.nhaarman.mockito_kotlin.mock
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.never
import org.mockito.Mockito.verify

class BrowserViewModelTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var queryObserver: Observer<String>
    private lateinit var navigationObserver: Observer<Command>
    private lateinit var termsOfServiceStore: TermsOfServiceStore
    private lateinit var mockStringResolver: StringResolver
    private lateinit var testee: BrowserViewModel

    private val testOmnibarConverter: OmnibarEntryConverter = object : OmnibarEntryConverter {
        override fun convertUri(input: String): String = "duckduckgo.com"
        override fun isWebUrl(inputQuery: String): Boolean = true
        override fun convertQueryToUri(inputQuery: String): Uri = Uri.parse("duckduckgo.com")
    }

    @Before
    fun before() {
        mockStringResolver = mock()
        queryObserver = mock()
        navigationObserver = mock()
        termsOfServiceStore = mock()
        testee = BrowserViewModel(testOmnibarConverter, DuckDuckGoUrlDetector(), termsOfServiceStore, TrackerNetworks(), PrivacyMonitorRepository(), object : StringResolver {
            override fun getString(stringId: Int): String = ""
            override fun getString(stringId: Int, vararg formatArgs: Any): String = ""
        }, SpecialUrlDetector())
        testee.url.observeForever(queryObserver)
        testee.command.observeForever(navigationObserver)
    }

    @After
    fun after() {
        testee.url.removeObserver(queryObserver)
        testee.command.removeObserver(navigationObserver)
    }

    @Test
    fun whenEmptyInputQueryThenNoQueryMadeAvailableToActivity() {
        testee.onUserSubmittedQuery("")
        verify(queryObserver, never()).onChanged(ArgumentMatchers.anyString())
    }

    @Test
    fun whenBlankInputQueryThenNoQueryMadeAvailableToActivity() {
        testee.onUserSubmittedQuery("     ")
        verify(queryObserver, never()).onChanged(ArgumentMatchers.anyString())
    }

    @Test
    fun whenNonEmptyInputThenQueryMadeAvailableToActivity() {
        testee.onUserSubmittedQuery("foo")
        verify(queryObserver).onChanged(ArgumentMatchers.anyString())
    }

    @Test
    fun whenViewModelNotifiedThatWebViewIsLoadingThenViewStateIsUpdated() {
        testee.loadingStarted()
        assertTrue(testee.viewState.value!!.isLoading)
    }

    @Test
    fun whenViewModelNotifiedThatWebViewHasFinishedLoadingThenViewStateIsUpdated() {
        testee.loadingFinished()
        assertFalse(testee.viewState.value!!.isLoading)
    }

    @Test
    fun whenViewModelNotifiedThatUrlGotFocusThenViewStateIsUpdated() {
        testee.onOmnibarInputStateChanged("", true)
        assertTrue(testee.viewState.value!!.isEditing)
    }

    @Test
    fun whenViewModelNotifiedThatUrlLostFocusThenViewStateIsUpdated() {
        testee.onOmnibarInputStateChanged("", false)
        assertFalse(testee.viewState.value!!.isEditing)
    }

    @Test
    fun whenNoOmnibarTextEverEnteredThenViewStateHasNull() {
        assertNull(testee.viewState.value!!.omnibarText)
    }

    @Test
    fun whenUrlChangedThenViewStateIsUpdated() {
        testee.urlChanged("duckduckgo.com")
        assertEquals("duckduckgo.com", testee.viewState.value!!.omnibarText)
    }

    @Test
    fun whenUrlChangedWithDuckDuckGoUrlContainingQueryThenUrlRewrittenToContainQuery() {
        testee.urlChanged("http://duckduckgo.com?q=test")
        assertEquals("test", testee.viewState.value!!.omnibarText)
    }

    @Test
    fun whenUrlChangedWithDuckDuckGoUrlNotContainingQueryThenFullUrlShown() {
        testee.urlChanged("http://duckduckgo.com")
        assertEquals("http://duckduckgo.com", testee.viewState.value!!.omnibarText)
    }

    @Test
    fun whenUrlChangedWithNonDuckDuckGoUrlThenFullUrlShown() {
        testee.urlChanged("http://example.com")
        assertEquals("http://example.com", testee.viewState.value!!.omnibarText)
    }

    @Test
    fun whenSharedTextReceivedThenNavigationTriggered() {
        testee.onSharedTextReceived("http://example.com")
        val captor: ArgumentCaptor<Command> = ArgumentCaptor.forClass(Command::class.java)
        verify(navigationObserver).onChanged(captor.capture())
        assertNotNull(captor.value)
        assertTrue(captor.value is Navigate)
    }

    @Test
    fun whenViewModelGetsProgressUpdateThenViewStateIsUpdated() {
        testee.progressChanged(0)
        assertEquals(0, testee.viewState.value!!.progress)

        testee.progressChanged(50)
        assertEquals(50, testee.viewState.value!!.progress)

        testee.progressChanged(100)
        assertEquals(100, testee.viewState.value!!.progress)
    }

    @Test
    fun whenUserDismissesKeyboardBeforeBrowserShownThenShouldNavigateToLandingPage() {
        testee.userDismissedKeyboard()
        verify(navigationObserver).onChanged(ArgumentMatchers.any(LandingPage::class.java))
    }

    @Test
    fun whenUserDismissesKeyboardAfterBrowserShownThenShouldNotNavigateToLandingPage() {
        testee.urlChanged("")
        verify(navigationObserver, never()).onChanged(ArgumentMatchers.any(LandingPage::class.java))
    }

    @Test
    fun whenUserDismissesKeyboardBeforeBrowserShownThenShouldConsumeBackButtonEvent() {
        assertTrue(testee.userDismissedKeyboard())
    }

    @Test
    fun whenUserDismissesKeyboardAfterBrowserShownThenShouldNotConsumeBackButtonEvent() {
        testee.urlChanged("")
        assertFalse(testee.userDismissedKeyboard())
    }

    @Test
    fun whenLoadingStartedThenPrivacyGradeIsCleared() {
        testee.loadingStarted()
        assertNull(testee.privacyGrade.value)
    }

    @Test
    fun whenUrlChangedThenPrivacyGradeIsReset() {
        testee.urlChanged("https://example.com")
        assertEquals(PrivacyGrade.B, testee.privacyGrade.value)
    }

    @Test
    fun whenTrackerDetectedThenPrivacyGradeIsUpdated() {
        testee.urlChanged("https://example.com")
        testee.trackerDetected(TrackingEvent("", "", null, false))
        assertEquals(PrivacyGrade.C, testee.privacyGrade.value)
    }

    @Test
    fun whenInitialisedThenPrivacyGradeIsNotShown() {
        assertFalse(testee.viewState.value!!.showPrivacyGrade)
    }

    @Test
    fun whenOmnibarInputDoesNotHaveFocusThenPrivacyGradeIsShown() {
        testee.onOmnibarInputStateChanged("", false)
        assertTrue(testee.viewState.value!!.showPrivacyGrade)
    }

    @Test
    fun whenOmnibarInputHasFocusThenPrivacyGradeIsNotShown() {
        testee.onOmnibarInputStateChanged("", true)
        assertFalse(testee.viewState.value!!.showPrivacyGrade)
    }

    @Test
    fun whenInitialisedThenFireButtonIsShown() {
        assertTrue(testee.viewState.value!!.showFireButton)
    }

    @Test
    fun whenOmnibarInputDoesNotHaveFocusThenFireButtonIsShown() {
        testee.onOmnibarInputStateChanged("", false)
        assertTrue(testee.viewState.value!!.showFireButton)
    }

    @Test
    fun whenOmnibarInputHasFocusThenFireButtonIsNotShown() {
        testee.onOmnibarInputStateChanged("", true)
        assertFalse(testee.viewState.value!!.showFireButton)
    }

}
