/*
Copyright 2012 Aphid Mobile

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

package com.ashish.ui.view;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.Context;
import android.database.DataSetObserver;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.AbsListView;
import android.widget.Adapter;
import android.widget.AdapterView;

import junit.framework.Assert;

import java.util.LinkedList;

public class CardDeckAdapterView extends AdapterView<Adapter> {

    private static final String TAG = "CardDeckAdapterView";

    private Adapter mAdapter;
    private View mViewOnDisplay;
    private View mPreviousViewDisplayed;
    private View mNextViewToDisplay;
    private GestureDetector mGestureDetector;

    private LinkedList<View> mReleasedViews;

    private int mTouchSlop;
    private int mFlingSlop;
    // Indicates the index of the item in the adapter (the 1st element is at index = 0)
    private int mVisibleItemIndexInAdapter;
    // Total number of items in the adapter
    private int mAdapterDataCount;
    private float mDownEventX;
    private float mLastX;
    private boolean isLeftDrag, isRightDrag;
    private boolean isInAnimation;
    // Index of item to be shown on the screen (the 1st element in the adapter is at index = size - 1 in the buffer)
    private int mVisibleItemIndexInBuffer;
    // Indicates the index of an item in the adapter that needs to be loaded in the buffer (Since it indicates index in adapter it moves from 0 to size - 1)
    // The items as they are fetched from the adapter (using this index) are added to buffer at 0th position. So, the items that were added first keep moving down
    // in the buffer
    private int mNextItemIndexToLoadInBufferFromAdapter;

    private static final int BUFFER_SIZE = 3;
    private static final float ZOOM_OUT_SCALE_FACTOR = 0.8f;
    private static final float ZOOM_IN_SCALE_FACTOR = 1.0f;
    private static final int MAX_RELEASED_VIEWS_SIZE = 1;


    public CardDeckAdapterView(Context context) {
        super(context);
        init();
    }

    public CardDeckAdapterView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CardDeckAdapterView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        ViewConfiguration viewConfiguration = ViewConfiguration.get(getContext());
        mTouchSlop = viewConfiguration.getScaledTouchSlop();
        mFlingSlop = viewConfiguration.getScaledMinimumFlingVelocity();
        mGestureDetector = new GestureDetector(getContext(), new GestureListener());
        mReleasedViews = new LinkedList<>();
        isLeftDrag = false;
        isRightDrag = false;
    }

    @Override
    public Adapter getAdapter() {
        return mAdapter;
    }

    @Override
    public void setAdapter(Adapter adapter) {
        setAdapter(adapter, 0);
    }

    @Override
    public View getSelectedView() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setSelection(int position) {
        if (mAdapter == null) {
            return;
        }

        releaseViews();

        mViewOnDisplay = null;
        mVisibleItemIndexInAdapter = position;
        if (mVisibleItemIndexInAdapter >= mAdapterDataCount) {
            mVisibleItemIndexInAdapter = 0; // 1st element in the adapter is the last element in the buffer
        }
        initializeStartingBufferIndexes();

        ensureFull(false);

        requestLayout();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        for (int i = 0; i < getChildCount(); i++) {
            View view = getChildAt(i);
            view.layout(left, top, right, bottom);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            assert child != null;
            child.measure(widthMeasureSpec, heightMeasureSpec);
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return handleTouchEvent(ev, false);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return handleTouchEvent(event, true);
    }

    private boolean handleTouchEvent(MotionEvent event, boolean isOnTouchEvent) {

        if (isInAnimation) {
            return isOnTouchEvent;
        }
        if (mGestureDetector.onTouchEvent(event)) {
            return true;
        }
        switch(event.getAction()) {

            case MotionEvent.ACTION_DOWN:
                mDownEventX = event.getX();
                mLastX = -1;
                return isOnTouchEvent;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                float movementDiffX = Math.abs(mDownEventX - event.getX());
                if (movementDiffX > 0 && !isLeftDrag && !isRightDrag) {
                    animateRestore(mViewOnDisplay);
                } else if (movementDiffX >= getWidth() / 2) {
                    if (isLeftDrag && !isRightDrag) {
                        animateLeftDrag(mViewOnDisplay, mNextViewToDisplay, -getWidth());
                    } else if (!isLeftDrag && isRightDrag) {
                        animateRightDrag(mViewOnDisplay, mPreviousViewDisplayed, 0);
                    }
                } else if (movementDiffX < getWidth() / 2) {
                    if (isLeftDrag && !isRightDrag) {
                        isLeftDrag = false;
                        isRightDrag = false;
                        animateRightDrag(mNextViewToDisplay, mViewOnDisplay, 0);
                    } else if (!isLeftDrag && isRightDrag) {
                        isLeftDrag = false;
                        isRightDrag = false;
                        animateLeftDrag(mPreviousViewDisplayed, mViewOnDisplay, -getWidth());
                    }
                }
                return isOnTouchEvent;
            case MotionEvent.ACTION_MOVE:
                float newX = event.getX();
                int deltaX = (int) (mDownEventX - newX);
                float ratio = (float) deltaX / getWidth();
                if (deltaX > 0) {
                    // Left drag
                    // There is a possibility that this event is a continuation event to a right drag folowed by left drag in one motion itself
                    // where the user has dragged past the point where he started motion (down event)
                    // In that case we need to reset the previous and current screen because they were displaced because of right drag movement
                    if (isRightDrag) {
                        mViewOnDisplay.setX(0);
                        mViewOnDisplay.setScaleX(ZOOM_IN_SCALE_FACTOR);
                        mViewOnDisplay.setScaleY(ZOOM_IN_SCALE_FACTOR);
                        mPreviousViewDisplayed.setX(-getWidth());
                    }
                    isLeftDrag = true;
                    isRightDrag = false;
                    if (mNextViewToDisplay != null) {
                        mViewOnDisplay.setX(-deltaX);
                        float scalingFactor = (float) (ZOOM_OUT_SCALE_FACTOR + ((ZOOM_IN_SCALE_FACTOR - ZOOM_OUT_SCALE_FACTOR) * ratio));
                        mNextViewToDisplay.setScaleX(scalingFactor);
                        mNextViewToDisplay.setScaleY(scalingFactor);
                    } else {
                        isLeftDrag = false;
                        ratio = (float) deltaX / (10 * getWidth());
                        float scalingFactor = (ZOOM_IN_SCALE_FACTOR + ratio);
                        mViewOnDisplay.setScaleX(scalingFactor);
                        mViewOnDisplay.setScaleY(scalingFactor);
                    }
                } else {
                    // Right drag
                    // There is a possibility where user first moves left and then moves right and moves right past the point where
                    // he started left movement. In that case we need to bring current view back to normal nd next view also to normal
                    // because otherwise because of left movement before this, they were displaced.
                    if (isLeftDrag) {
                        // First left movement was done and then in same movement right was done past the initial touch of down event
                        // that is why delta is < 0
                        mViewOnDisplay.setX(0);
                        mNextViewToDisplay.setScaleX(ZOOM_OUT_SCALE_FACTOR);
                        mNextViewToDisplay.setScaleY(ZOOM_OUT_SCALE_FACTOR);
                    }
                    isLeftDrag = false;
                    isRightDrag = true;
                    if (mPreviousViewDisplayed != null) {
                        mPreviousViewDisplayed.setX(-getWidth() - deltaX);
                        float scalingFactor = (float) (ZOOM_IN_SCALE_FACTOR - ((ZOOM_IN_SCALE_FACTOR - ZOOM_OUT_SCALE_FACTOR) * Math.abs(ratio)));
                        mViewOnDisplay.setScaleX(scalingFactor);
                        mViewOnDisplay.setScaleY(scalingFactor);
                    } else {
                        isRightDrag = false;
                        ratio = (float) Math.abs(deltaX) / (10 * getWidth());
                        float scalingFactor = (float) (ZOOM_IN_SCALE_FACTOR + ratio);
                        mViewOnDisplay.setScaleX(scalingFactor);
                        mViewOnDisplay.setScaleY(scalingFactor);
                    }
                }
                mLastX = newX;
                return true;
        }
        return false;
    }

    /**
     * Animates the restore of the view to its original dimensions after we pull 1st and last item in the list because
     * as we pull 1st and last item it zooms in.
     * @param currentView
     */
    private void animateRestore(View currentView) {
        if (isInAnimation) {
            return;
        }
        isInAnimation = true;
        PropertyValuesHolder scaleX = PropertyValuesHolder.ofFloat("scaleX", ZOOM_IN_SCALE_FACTOR);
        PropertyValuesHolder scaleY = PropertyValuesHolder.ofFloat("scaleY", ZOOM_IN_SCALE_FACTOR);
        PropertyValuesHolder x = PropertyValuesHolder.ofFloat("x", 0);
        ObjectAnimator zoomInAnimator = ObjectAnimator.ofPropertyValuesHolder(currentView, scaleX, scaleY, x);

        AnimatorSet s = new AnimatorSet();
        s.playTogether(zoomInAnimator);
        s.addListener(animatorListener);
        s.start();
    }

    /**
     * Animates the left drag movement to display next element in the list
     * This method is called for items with index 0 to number of elements - 2
     * @param currentView
     * @param nextView
     * @param finalX
     */
    private void animateLeftDrag(View currentView, View nextView, float finalX) {
        if (isInAnimation) {
            return;
        }
        isInAnimation = true;
        PropertyValuesHolder x = PropertyValuesHolder.ofFloat("x", finalX);
        PropertyValuesHolder scaleX = PropertyValuesHolder.ofFloat("scaleX", ZOOM_IN_SCALE_FACTOR);
        PropertyValuesHolder scaleY = PropertyValuesHolder.ofFloat("scaleY", ZOOM_IN_SCALE_FACTOR);
        PropertyValuesHolder nextViewX = PropertyValuesHolder.ofFloat("x", 0);
        ObjectAnimator dragAnimator = ObjectAnimator.ofPropertyValuesHolder(currentView, x);
        ObjectAnimator zoomInAnimator = ObjectAnimator.ofPropertyValuesHolder(nextView, scaleX, scaleY, nextViewX);

        AnimatorSet s = new AnimatorSet();
        s.playTogether(dragAnimator, zoomInAnimator);
        s.addListener(animatorListener);
        s.start();

    }

    /**
     * Animates right drag to display the previous element. This method is called for
     * items with index = 1 to total number of elements in adapter - 1
     * @param currentView
     * @param prevView
     * @param finalX
     */
    private void animateRightDrag(View currentView, View prevView, float finalX) {
        if (isInAnimation) {
            return;
        }
        isInAnimation = true;
        PropertyValuesHolder x = PropertyValuesHolder.ofFloat("x", finalX);
        PropertyValuesHolder scaleX = PropertyValuesHolder.ofFloat("scaleX", ZOOM_OUT_SCALE_FACTOR);
        PropertyValuesHolder scaleY = PropertyValuesHolder.ofFloat("scaleY", ZOOM_OUT_SCALE_FACTOR);
        ObjectAnimator dragAnimator = ObjectAnimator.ofPropertyValuesHolder(prevView, x);
        ObjectAnimator zoomOutAnimator = ObjectAnimator.ofPropertyValuesHolder(currentView, scaleX, scaleY);

        AnimatorSet s = new AnimatorSet();
        s.playTogether(dragAnimator, zoomOutAnimator);
        s.addListener(animatorListener);
        s.start();
    }

    public void setAdapter(Adapter adapter, int initialPosition) {
        if (this.mAdapter != null) {
            this.mAdapter.unregisterDataSetObserver(mDataSetObserver);
        }

        Assert.assertNotNull("adapter should not be null", adapter);

        this.mAdapter = adapter;
        mAdapterDataCount = adapter.getCount();

        this.mAdapter.registerDataSetObserver(mDataSetObserver);

        setSelection(initialPosition);
    }

    /**
     * This method only keeps the buffer full all the time unless the buffer size is bigger than the size of adapter
     * @param addAtTop Indicates whether we need to add page/item to top of scree or at the bottom. Adding to the
     *                 top of screen means adding at the last position in the buffer
     */
    private void ensureFull(boolean addAtTop) {
        int index = mNextItemIndexToLoadInBufferFromAdapter;
        if (addAtTop) {
            index = mVisibleItemIndexInAdapter - 1;
        }
        while (((!addAtTop && index < mAdapter.getCount()) || (addAtTop && index >= 0)) && getChildCount() < BUFFER_SIZE) {
            View releasedView = mReleasedViews == null || mReleasedViews.size() == 0? null: mReleasedViews.removeFirst();
            View view = mAdapter.getView(index, releasedView, this);
            if (releasedView != null && view != releasedView) {
                addReleasedView(releasedView);
            }
            if (index < mVisibleItemIndexInAdapter) {
                view.setX(-getWidth());
                view.setScaleX(ZOOM_IN_SCALE_FACTOR);
                view.setScaleY(ZOOM_IN_SCALE_FACTOR);
            } else if (index > mVisibleItemIndexInAdapter) {
                view.setX(0);
                view.setScaleX(ZOOM_OUT_SCALE_FACTOR);
                view.setScaleY(ZOOM_OUT_SCALE_FACTOR);
            }
            LayoutParams params = view.getLayoutParams();
            if (params == null) {
                params =
                        new AbsListView.LayoutParams(LayoutParams.FILL_PARENT,
                                LayoutParams.WRAP_CONTENT, 0);
            }
            if (view != releasedView) {
                addViewInLayout(view, addAtTop ? -1: 0, params, true);
            } else {
                attachViewToParent(view, addAtTop? -1: 0, params);
            }

            requestLayout();

            index = addAtTop? index - 1 : index + 1;
        }

        // addAtTop is true only when we scrolled through the list all the way and then we scrolled back
        // to the top of the list
        if (!addAtTop) {
            mNextItemIndexToLoadInBufferFromAdapter = index;
        } else {
            mNextItemIndexToLoadInBufferFromAdapter = mNextItemIndexToLoadInBufferFromAdapter - (mVisibleItemIndexInAdapter - 1 - index);
        }

        if (getChildCount() != 0) {
            mViewOnDisplay = getChildAt(mVisibleItemIndexInBuffer);
            mPreviousViewDisplayed = mVisibleItemIndexInBuffer == BUFFER_SIZE - 1? null: getChildAt(mVisibleItemIndexInBuffer + 1);
            mNextViewToDisplay = mVisibleItemIndexInBuffer == 0 ? null : getChildAt(mVisibleItemIndexInBuffer - 1);
        }
    }

    /**
     * Initializes the next item to load from the adapter in to the buffer and also the
     * index of the item that will be displayed in the buffer
     * Buffer (except the 1st item in the adapter) always stores the previous element to the element displayed on screen
     * It also stores the next element to the element currently shown on screen (except the last element in the adapter)
     */
    private void initializeStartingBufferIndexes() {
        if (mAdapterDataCount <= BUFFER_SIZE || mVisibleItemIndexInAdapter == 0) {
            mNextItemIndexToLoadInBufferFromAdapter = 0;
            if (mAdapterDataCount <= BUFFER_SIZE) {
                mVisibleItemIndexInBuffer = mAdapterDataCount - 1 - mVisibleItemIndexInAdapter;
            } else {
                mVisibleItemIndexInBuffer = BUFFER_SIZE - 1 - mVisibleItemIndexInAdapter;
            }
        } else if (mVisibleItemIndexInAdapter + BUFFER_SIZE - 1 <= mAdapterDataCount) {
            mNextItemIndexToLoadInBufferFromAdapter = mVisibleItemIndexInAdapter - 1;
            mVisibleItemIndexInBuffer = 1;
        } else {
            mNextItemIndexToLoadInBufferFromAdapter = mAdapterDataCount - BUFFER_SIZE;
            mVisibleItemIndexInBuffer = mAdapterDataCount - 1 - mVisibleItemIndexInAdapter;
        }
        mVisibleItemIndexInBuffer = mVisibleItemIndexInBuffer < 0? 0: mVisibleItemIndexInBuffer;
    }

    /**
     * Move to next element. If needed, it also fetches the next element from adapter
     * and stores in the buffer
     */
    private void moveToNextElement() {
        if (mVisibleItemIndexInBuffer == 1) {
            if (mVisibleItemIndexInAdapter + 1 < mAdapterDataCount - 1) {
                // Remove the top most element from the screen (bottom one in the buffer)
                // No need to move the buffer index because anyways in ensureFull method
                // the next item is going to move to the 1st index in buffer
                releaseView(getChildAt(BUFFER_SIZE - 1));
                mVisibleItemIndexInAdapter++;
                ensureFull(false);
            } else {
                mVisibleItemIndexInBuffer--;
                mVisibleItemIndexInAdapter++;
                ensureFull(false);
            }
        } else if (mVisibleItemIndexInBuffer == 0) {
            // No more items to move to
        } else {
            mVisibleItemIndexInBuffer--;
            mVisibleItemIndexInAdapter++;
            ensureFull(false);
        }
    }

    /**
     * Moves to the previous element. If needed, it fetches the previous element
     * from the adapter and adds it to the buffer
     */
    private void moveToPrevElement() {
        int limit = BUFFER_SIZE;
        if (mAdapterDataCount <= BUFFER_SIZE) {
            limit = mAdapterDataCount;
        }
        if (mVisibleItemIndexInBuffer == limit - 2) {
            if (mVisibleItemIndexInAdapter > 1) {
                // Remove the bottom most element from the screen (top one in the buffer)
                releaseView(getChildAt(0));
                mVisibleItemIndexInAdapter--;
                ensureFull(true);
            } else {
                mVisibleItemIndexInBuffer++;
                mVisibleItemIndexInAdapter--;
                ensureFull(true);
            }
        } else if (mVisibleItemIndexInBuffer == limit - 1) {
            // No more items to move to
        } else {
            mVisibleItemIndexInBuffer++;
            mVisibleItemIndexInAdapter--;
            ensureFull(true);
        }
    }

    /**
     * Removes a view from the list and adds it to be re-used
     * @param view
     */
    private void releaseView(View view) {
        detachViewFromParent(view);
        addReleasedView(view);
    }

    /**
     * Releases all views
     */
    private void releaseViews() {
        for (int index = 0; index < getChildCount(); ) {
            View view = getChildAt(index);
            releaseView(view);
        }
        mViewOnDisplay = null;
        mPreviousViewDisplayed = null;
        mNextViewToDisplay = null;
        mNextItemIndexToLoadInBufferFromAdapter = -1;
        mVisibleItemIndexInBuffer = -1;
        if (mAdapter != null) {
            mAdapterDataCount = mAdapter.getCount();
        }
    }

    /**
     * Adds the released view in the released view linked list to be re-used
     * @param view
     */
    private void addReleasedView(View view) {
        if (mReleasedViews == null) {
            mReleasedViews = new LinkedList<>();
        }
        if (mReleasedViews.size() < MAX_RELEASED_VIEWS_SIZE) {
            mReleasedViews.add(view);
        }
    }

    /**
     * Listens to data set change events
     */
    private final DataSetObserver mDataSetObserver = new DataSetObserver() {
        @Override
        public void onChanged() {
            super.onChanged();
            int oldAdapterCount = mAdapterDataCount;
            releaseViews();
            if (oldAdapterCount <= 0 && mAdapterDataCount > 0) {
                mVisibleItemIndexInAdapter = 0;
                initializeStartingBufferIndexes();
            } else if (mAdapterDataCount <= mVisibleItemIndexInAdapter) {
                mVisibleItemIndexInAdapter = mAdapterDataCount - 1;
                initializeStartingBufferIndexes();
            }
            ensureFull(false);
        }

        @Override
        public void onInvalidated() {
            super.onInvalidated();
            mVisibleItemIndexInAdapter = 0;
            releaseViews();
        }
    };

    /**
     * Listens to animation events
     */
    private Animator.AnimatorListener animatorListener = new Animator.AnimatorListener() {
        @Override
        public void onAnimationStart(Animator animation) {

        }

        @Override
        public void onAnimationEnd(Animator animation) {
            if (isLeftDrag && !isRightDrag) {
                moveToNextElement();
            } else if (!isLeftDrag && isRightDrag) {
                moveToPrevElement();
            }
            isLeftDrag = isRightDrag = false;
            isInAnimation = false;
        }

        @Override
        public void onAnimationCancel(Animator animation) {

        }

        @Override
        public void onAnimationRepeat(Animator animation) {

        }
    };

    /**
     * Listens to swipe/fling events
     */
    private class GestureListener extends GestureDetector.SimpleOnGestureListener {

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            // e1 is down event
            // e2 is up event
            float initX = e1.getX();
            float finalX = e2.getX();
            float deltaX = initX - finalX;
            // if deltaX > 0 - left swipe
            if (deltaX > 0 && mNextViewToDisplay != null) {
                // Left swipe
                isLeftDrag = true;
                isRightDrag = false;
                animateLeftDrag(mViewOnDisplay, mNextViewToDisplay, -getWidth());
                return true;
            } else if (deltaX < 0 && mPreviousViewDisplayed != null) {
                // Right drag
                isLeftDrag = false;
                isRightDrag = true;
                animateRightDrag(mViewOnDisplay, mPreviousViewDisplayed, 0);
                return true;
            } else {
                isLeftDrag = false;
                isRightDrag = false;
                return false;
            }
        }
    }
}

// TODO: Add fling slop and touch slop