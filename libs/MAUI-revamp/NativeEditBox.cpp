/* Copyright 2013 David Axmark

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

	http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

/**
 * \file NativeEditBox.h
 * \brief Class that implements wrapper for maTextBox.
 * \author Patrick Broman and Niklas Nummelin
 */

#include "NativeEditBox.h"
#ifdef USE_NEWLIB
#include <stdlib.h>
#include <wchar.h>
#include <assert.h>
#include <stdio.h>
#else
#include <mastdlib.h>
#include <mavsprintf.h>
#include <mawvsprintf.h>
#include <maassert.h>
#endif

namespace MAUI {

void NativeEditBox::textBoxClosed(int res, int length) {
	if(res == MA_TB_RES_OK) {
		MAUI_LOG("length: %d", length);
		String str;
		str.resize(length);

		//sprintf(str.pointer(), "%S", mString);	// insufficient
		for(int i=0; i<length; i++) {
			if(mString[i] < 0xff)
				str[i] = (byte)mString[i];
			else
				str[i] = mListener->nativeEditUnicode(mString[i]);
		}

		setCaption(str);
		MAUI_LOG("%S", mString);
		requestRepaint();
		ListenerSet_fire(
			NativeEditBoxListener,
			mEditBoxListeners,
			nativeEditFinished(this, mCaption));

	}
	Environment::getEnvironment().removeTextBoxListener(this);
}

NativeEditBox::NativeEditBox(
	NativeEditBoxListener* mainListener,
	int x,
	int y,
	int width,
	int height,
	int maxSize,
	int options,
	const String& initialText,
	const WString& titleString)
		: Label(x, y, width, height, initialText),
		mTitleString(titleString),
		mString(NULL),
		mOptions(options),
		mEditBoxListeners(false),
		mListener(NULL)
{
	addNativeEditBoxListener(mainListener);
	mListener = mainListener;
	setMaxSize(maxSize);
	setCaption(initialText);
}

NativeEditBox::~NativeEditBox() {
	if(mString) {
		delete mString;
	}
}

void NativeEditBox::setOptions(int options) {
	mOptions = options;
}

void NativeEditBox::setMaxSize(int size) {
	if(size == 0) {
		maPanic(1, "NativeEditBox: Invalid max size!!");
	}
	wchar_t *oldString = mString;
	mString = new wchar_t[size];
	mString[0] = 0;

	if(oldString)
	{
		int minSize = (mMaxSize<size)?mMaxSize:size;
		memcpy(mString, oldString, minSize);
		mString[size-1] = 0;
		delete oldString;
	}

	mMaxSize = size;
}

bool NativeEditBox::keyPressed(int keyCode, int nativeCode) {
	if(!mEnabled)
		return false;
	if(keyCode == MAK_FIRE) {
		activate();
		return true;
	}
	return false;
}

bool NativeEditBox::pointerPressed(MAPoint2d p, int id) {
	if(!mEnabled)
		return false;
	mStartX = p.x;
	mStartY = p.y;
	return true;
}

bool NativeEditBox::pointerMoved(MAPoint2d p, int id) {
	if(!mEnabled)
		return false;
	p.x-=mStartX;
	p.y-=mStartY;
	if((abs(p.x)<7) && (abs(p.y)<7)) {
		return true;
	}
	else {
		return false;
	}
}

bool NativeEditBox::pointerReleased(MAPoint2d p, int id) {
	if(!mEnabled)
		return false;
	activate();
	return false;
}

void NativeEditBox::activate() {
#ifdef USE_NEWLIB
	swprintf(mString, mMaxSize, L"%s", mCaption.c_str());
#else
	wsprintf(mString, L"%s", mCaption.c_str());
#endif
	int res =
		maTextBox(
			(const wchar*)mTitleString.c_str(),
			(wchar*)mString,
			(wchar*)mString,
			mMaxSize,
			mOptions);
	if(res == MA_TB_RES_TYPE_UNAVAILABLE &&
		(mOptions & MA_TB_TYPE_MASK) == MA_TB_TYPE_SINGLE_LINE)
	{
		mOptions = MA_TB_TYPE_ANY | (mOptions & ~MA_TB_TYPE_MASK);
		res = maTextBox(
			(const wchar*)mTitleString.c_str(),
			(wchar*)mString,
			(wchar*)mString,
			mMaxSize,
			mOptions);
	}
	if(res < 0) {
		PANIC_MESSAGE("maTextBox failed");
	}
	Environment::getEnvironment().addTextBoxListener(this);
}

void NativeEditBox::setTitleString(const WString& titleString) {
	mTitleString = titleString;
}

const WString& NativeEditBox::getTitleString() const {
	return mTitleString;
}

void NativeEditBox::setCaption(const String& caption) {
	if(caption.length() > mMaxSize) {
		setMaxSize(caption.length()+1);
	}
	Label::setCaption(caption);
}

void NativeEditBox::addNativeEditBoxListener(NativeEditBoxListener* wl) {
	mEditBoxListeners.add(wl);
}

void NativeEditBox::removeNativeEditBoxListener(NativeEditBoxListener* wl) {
	MAASSERT(wl != mListener);
	mEditBoxListeners.remove(wl);
}

} // namespace MAUI
