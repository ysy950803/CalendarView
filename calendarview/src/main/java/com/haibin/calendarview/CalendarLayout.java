package com.haibin.calendarview;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.AbsListView;
import android.widget.LinearLayout;

/**
 * 日历布局
 */
public class CalendarLayout extends LinearLayout {

    /**
     * 周月视图
     */
    private static final int CALENDAR_SHOW_MODE_BOTH_MONTH_WEEK_VIEW = 0;

    /**
     * 仅周视图
     */
    private static final int CALENDAR_SHOW_MODE_ONLY_WEEK_VIEW = 1;

    /**
     * 仅月视图
     */
    private static final int CALENDAR_SHOW_MODE_ONLY_MONTH_VIEW = 2;

    /**
     * 默认展开
     */
    public static final int STATUS_EXPAND = 0;

    /**
     * 默认收缩
     */
    public static final int STATUS_SHRINK = 1;

    /**
     * 默认状态
     */
    private int mDefaultStatus;

    /**
     * 星期栏
     */
    WeekBar mWeekBar;

    /**
     * 自定义ViewPager，月视图
     */
    MonthViewPager mMonthView;

    /**
     * 自定义的周视图
     */
    WeekViewPager mWeekPager;

    /**
     * 年视图
     */
    YearViewSelectLayout mYearView;

    /**
     * ContentView
     */
    ViewGroup mContentView;

    /**
     * 默认手势
     */
    private static final int GESTURE_MODE_DEFAULT = 0;

    /**
     * 仅日历有效
     */
    private static final int GESTURE_MODE_ONLY_CALENDAR = 1;

    /**
     * 禁用手势
     */
    private static final int GESTURE_MODE_DISABLED = 2;

    /**
     * 手势模式
     */
    private int mGestureMode;

    private int mCalendarShowMode;

    private int mTouchSlop;
    private int mContentViewTranslateY; // ContentView 可滑动的最大距离距离，固定
    private int mViewPagerTranslateY = 0; // ViewPager可以平移的距离，不代表mMonthView的平移距离

    private float downY;
    private float mLastY;
    private boolean isAnimating = false;
    private boolean isTouchDownContentView;
    private boolean isContentViewAlignedTop;

    /**
     * 内容布局id
     */
    private int mContentViewId;

    /**
     * 手速判断
     */
    private VelocityTracker mVelocityTracker;
    private int mMaximumVelocity;

    private int mItemHeight;

    private CalendarViewDelegate mDelegate;
    private ContentViewListener mContentViewListener;

    public CalendarLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(LinearLayout.VERTICAL);
        TypedArray array = context.obtainStyledAttributes(attrs, R.styleable.CalendarLayout);
        mContentViewId = array.getResourceId(R.styleable.CalendarLayout_calendar_content_view_id, 0);
        mDefaultStatus = array.getInt(R.styleable.CalendarLayout_default_status, STATUS_EXPAND);
        mCalendarShowMode = array.getInt(R.styleable.CalendarLayout_calendar_show_mode, CALENDAR_SHOW_MODE_BOTH_MONTH_WEEK_VIEW);
        mGestureMode = array.getInt(R.styleable.CalendarLayout_gesture_mode, GESTURE_MODE_DEFAULT);
        array.recycle();
        mVelocityTracker = VelocityTracker.obtain();
        final ViewConfiguration configuration = ViewConfiguration.get(context);
        mTouchSlop = configuration.getScaledTouchSlop();
        mMaximumVelocity = configuration.getScaledMaximumFlingVelocity();
    }

    public void resetDefaultStatus() {
        setDefaultStatus(mDefaultStatus);
    }

    public void setDefaultStatus(int status) {
        isContentViewAlignedTop = false;
        isTouchDownContentView = false;
        mDefaultStatus = status;
        if (status == STATUS_SHRINK) {
            shrink();
        } else if (status == STATUS_EXPAND) {
            expand();
        }
    }

    /**
     * 初始化
     *
     * @param delegate delegate
     */
    final void setup(CalendarViewDelegate delegate) {
        this.mDelegate = delegate;
        mItemHeight = mDelegate.getCalendarItemHeight();
        initCalendarPosition(delegate.mSelectedCalendar.isAvailable() ?
                delegate.mSelectedCalendar :
                delegate.createCurrentDate());
        updateContentViewTranslateY();
    }

    /**
     * 初始化当前时间的位置
     *
     * @param cur 当前日期时间
     */
    private void initCalendarPosition(Calendar cur) {
        int diff = CalendarUtil.getMonthViewStartDiff(cur, mDelegate.getWeekStart());
        int size = diff + cur.getDay() - 1;
        updateSelectPosition(size);
    }

    /**
     * 当前第几项被选中，更新平移量
     *
     * @param selectPosition 月视图被点击的position
     */
    final void updateSelectPosition(int selectPosition) {
        int line = (selectPosition + 7) / 7;
        mViewPagerTranslateY = (line - 1) * mItemHeight;
    }

    /**
     * 设置选中的周，更新位置
     *
     * @param week week
     */
    final void updateSelectWeek(int week) {
        mViewPagerTranslateY = (week - 1) * mItemHeight;
    }

    /**
     * 更新内容ContentView可平移的最大距离
     */
    void updateContentViewTranslateY() {
        Calendar calendar = mDelegate.mIndexCalendar;
        if (mDelegate.getMonthViewShowMode() == CalendarViewDelegate.MODE_ALL_MONTH) {
            mContentViewTranslateY = 5 * mItemHeight;
        } else {
            mContentViewTranslateY = CalendarUtil.getMonthViewHeight(calendar.getYear(),
                    calendar.getMonth(), mItemHeight, mDelegate.getWeekStart()) - mItemHeight;
        }
        // 已经显示周视图，则需要动态平移contentView的高度
        if (mWeekPager.getVisibility() == VISIBLE) {
            if (mContentView == null)
                return;
            translateContentView(-mContentViewTranslateY);
        }
    }

    /**
     * 更新日历项高度
     */
    final void updateCalendarItemHeight() {
        mItemHeight = mDelegate.getCalendarItemHeight();
        if (mContentView == null)
            return;
        Calendar calendar = mDelegate.mIndexCalendar;
        updateSelectWeek(CalendarUtil.getWeekFromDayInMonth(calendar, mDelegate.getWeekStart()));
        if (mDelegate.getMonthViewShowMode() == CalendarViewDelegate.MODE_ALL_MONTH) {
            mContentViewTranslateY = 5 * mItemHeight;
        } else {
            mContentViewTranslateY = CalendarUtil.getMonthViewHeight(calendar.getYear(), calendar.getMonth(),
                    mItemHeight, mDelegate.getWeekStart()) - mItemHeight;
        }
        translateViewPager();
        if (mWeekPager.getVisibility() == VISIBLE) {
            translateContentView(-mContentViewTranslateY);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mDelegate.isShowYearSelectedLayout || mContentView == null || isContentViewAlignedTop) {
            return false;
        }

        int action = event.getAction();
        float y = event.getY();
        mVelocityTracker.addMovement(event);
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mLastY = downY = y;
                isTouchDownContentView = downY > mContentView.getTranslationY() + mContentView.getTop();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (mGestureMode == GESTURE_MODE_DISABLED ||
                        mCalendarShowMode == CALENDAR_SHOW_MODE_ONLY_MONTH_VIEW ||
                        mCalendarShowMode == CALENDAR_SHOW_MODE_ONLY_WEEK_VIEW) {
                    // 禁用手势，或者只显示某种视图，不继续逻辑
                    return false;
                }

                float dy = y - mLastY;
                if (dy < 0) { // 向上滑动
                    // contentView已经平移到最大距离
                    if (mContentView.getTranslationY() + dy <= -mContentViewTranslateY) {
                        translateContentView(-mContentViewTranslateY);
                        translateViewPager();
                        mLastY = y;
                        onContentViewTranslateToTop();
                        break;
                    }
                } else { // 向下滑动
                    // contentView已经完全平移到底部
                    if (mContentView.getTranslationY() + dy >= 0) {
                        translateContentView(0);
                        translateViewPager();
                        mLastY = y;
                        break;
                    }
                }
                hideWeek();

                // 否则按比例平移
                translateContentView(mContentView.getTranslationY() + dy);
                translateViewPager();
                mLastY = y;
                break;
            case MotionEvent.ACTION_UP:
                if (mContentView.getTranslationY() == 0
                        || mContentView.getTranslationY() == -mContentViewTranslateY) {
                    break;
                }
                // 获取手速
                final VelocityTracker velocityTracker = mVelocityTracker;
                velocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);
                float mYVelocity = velocityTracker.getYVelocity();
                if (mYVelocity > 800 || event.getY() - downY > 0) {
                    expand();
                } else if (mYVelocity < -800 || event.getY() - downY < 0) {
                    shrink();
                }
                break;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (isAnimating) {
            return true;
        }
        if (mGestureMode == GESTURE_MODE_DISABLED) {
            return false;
        }
        if (mYearView == null ||
                mContentView == null ||
                mContentView.getVisibility() != VISIBLE) {
            return super.onInterceptTouchEvent(ev);
        }

        if (mCalendarShowMode == CALENDAR_SHOW_MODE_ONLY_MONTH_VIEW ||
                mCalendarShowMode == CALENDAR_SHOW_MODE_ONLY_WEEK_VIEW) {
            return false;
        }

        if (mYearView.getVisibility() == VISIBLE || mDelegate.isShowYearSelectedLayout) {
            return super.onInterceptTouchEvent(ev);
        }
        final int action = ev.getAction();
        float y = ev.getY();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mLastY = downY = y;
                break;
            case MotionEvent.ACTION_MOVE:
                float dy = y - mLastY;
                /*
                 * 如果向上滚动，且ViewPager已经收缩，不拦截事件
                 */
                if (dy < 0 && mContentView.getTranslationY() == -mContentViewTranslateY) {
                    return false;
                }
                /*
                 * 如果向下滚动，有2种情况处理，且y在ViewPager下方
                 * 1. RecyclerView 或者其它滚动的View，当contentView滚动到顶部时，拦截事件
                 * 2. 非滚动控件，直接拦截事件
                 */
                if (dy > 0 && mContentView.getTranslationY() == -mContentViewTranslateY
                        && y >= CalendarUtil.dipToPx(getContext(), 98)) {
                    if (!isScrollTop()) {
                        return false;
                    }
                }

                if (dy > 0 && mContentView.getTranslationY() == 0 && y >= CalendarUtil.dipToPx(getContext(), 98)) {
                    return false;
                }

                if (Math.abs(dy) > mTouchSlop) { // 大于mTouchSlop开始拦截事件，ContentView和ViewPager得到CANCEL事件
                    if ((dy > 0 && mContentView.getTranslationY() <= 0)
                            || (dy < 0 && mContentView.getTranslationY() >= -mContentViewTranslateY)) {
                        mLastY = y;
                        return true;
                    }
                }
                break;
        }
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (mContentView != null && mMonthView != null) {
            int year = mDelegate.mIndexCalendar.getYear();
            int month = mDelegate.mIndexCalendar.getMonth();

            int monthHeight = CalendarUtil.getMonthViewHeight(year, month,
                    mDelegate.getCalendarItemHeight(),
                    mDelegate.getWeekStart()) + CalendarUtil.dipToPx(getContext(), 41);
            int height = getHeight();

            if (monthHeight >= height && mMonthView.getHeight() > 0) {
                height = monthHeight;
                heightMeasureSpec = MeasureSpec.makeMeasureSpec(monthHeight +
                        CalendarUtil.dipToPx(getContext(), 41) +
                        mDelegate.getWeekBarHeight(), MeasureSpec.EXACTLY);
            } else if (monthHeight < height && mMonthView.getHeight() > 0) {
                heightMeasureSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY);
            }

            int h = height - mItemHeight
                    - (mDelegate != null ? mDelegate.getWeekBarHeight() :
                    CalendarUtil.dipToPx(getContext(), 40))
                    - CalendarUtil.dipToPx(getContext(), 1);
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            int heightSpec = MeasureSpec.makeMeasureSpec(h,
                    MeasureSpec.EXACTLY);
            mContentView.measure(widthMeasureSpec, heightSpec);
            mContentView.layout(mContentView.getLeft(), mContentView.getTop(), mContentView.getRight(), mContentView.getBottom());
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mMonthView = findViewById(R.id.vp_month);
        mWeekPager = findViewById(R.id.vp_week);
        mContentView = findViewById(mContentViewId);
        mYearView = findViewById(R.id.selectLayout);
        if (mContentView != null) {
            mContentView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        }
    }

    /**
     * 平移ViewPager月视图
     */
    private void translateViewPager() {
        float percent = mContentView.getTranslationY() * 1.0f / mContentViewTranslateY;
        mMonthView.setTranslationY(mViewPagerTranslateY * percent);
    }

    /**
     * 平移ContentView
     */
    private void translateContentView(float tY) {
        mContentView.setTranslationY(tY);
    }

    /**
     * 是否展开了
     *
     * @return isExpand
     */
    public final boolean isExpand() {
        return mContentView == null || mMonthView.getVisibility() == VISIBLE;
    }

    /**
     * 展开
     *
     * @return 展开是否成功
     */
    public boolean expand() {
        if (isAnimating ||
                mCalendarShowMode == CALENDAR_SHOW_MODE_ONLY_WEEK_VIEW ||
                mContentView == null)
            return false;
        if (mMonthView.getVisibility() != VISIBLE) {
            mWeekPager.setVisibility(GONE);
            onShowMonthView();
            mMonthView.setVisibility(VISIBLE);
        }
        ObjectAnimator objectAnimator = ObjectAnimator.ofFloat(mContentView,
                "translationY", mContentView.getTranslationY(), 0f);
        objectAnimator.setDuration(240);
        objectAnimator.addUpdateListener(animation -> {
            float currentValue = (Float) animation.getAnimatedValue();
            float percent = currentValue * 1.0f / mContentViewTranslateY;
            mMonthView.setTranslationY(mViewPagerTranslateY * percent);
            isAnimating = true;
        });
        objectAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                isAnimating = false;
                hideWeek();
            }
        });
        objectAnimator.start();
        return true;
    }

    /**
     * 收缩
     *
     * @return 成功或者失败
     */
    public boolean shrink() {
        if (isAnimating || mContentView == null) {
            return false;
        }
        ObjectAnimator objectAnimator = ObjectAnimator.ofFloat(mContentView,
                "translationY", mContentView.getTranslationY(), -mContentViewTranslateY);
        objectAnimator.setDuration(240);
        objectAnimator.addUpdateListener(animation -> {
            float currentValue = (Float) animation.getAnimatedValue();
            float percent = currentValue * 1.0f / mContentViewTranslateY;
            mMonthView.setTranslationY(mViewPagerTranslateY * percent);
            isAnimating = true;
        });
        objectAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                isAnimating = false;
                showWeek();
                onContentViewTranslateToTop();
            }
        });
        objectAnimator.start();
        return true;
    }

    /**
     * 初始化状态
     */
    final void initStatus() {
        if (mContentView == null) {
            return;
        }
        if ((mDefaultStatus == STATUS_SHRINK ||
                mCalendarShowMode == CALENDAR_SHOW_MODE_ONLY_WEEK_VIEW) &&
                mCalendarShowMode != CALENDAR_SHOW_MODE_ONLY_MONTH_VIEW) {
            post(new Runnable() {
                @Override
                public void run() {
                    ObjectAnimator objectAnimator = ObjectAnimator.ofFloat(mContentView,
                            "translationY", mContentView.getTranslationY(), -mContentViewTranslateY);
                    objectAnimator.setDuration(0);
                    objectAnimator.addUpdateListener(animation -> {
                        float currentValue = (Float) animation.getAnimatedValue();
                        float percent = currentValue * 1.0f / mContentViewTranslateY;
                        mMonthView.setTranslationY(mViewPagerTranslateY * percent);
                        isAnimating = true;
                    });
                    objectAnimator.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            super.onAnimationEnd(animation);
                            isAnimating = false;
                            showWeek();
                        }
                    });
                    objectAnimator.start();
                }
            });
        } else {
            if (mDelegate.mViewChangeListener == null) {
                return;
            }
            post(() -> mDelegate.mViewChangeListener.onViewChange(true));
        }
    }

    /**
     * 隐藏周视图
     */
    private void hideWeek() {
        onShowMonthView();
        mWeekPager.setVisibility(GONE);
        mMonthView.setVisibility(VISIBLE);
    }

    /**
     * 显示周视图
     */
    private void showWeek() {
        onShowWeekView();
        mWeekPager.getAdapter().notifyDataSetChanged();
        mWeekPager.setVisibility(VISIBLE);
        mMonthView.setVisibility(INVISIBLE);
    }

    /**
     * 周视图显示事件
     */
    private void onShowWeekView() {
        if (mWeekPager.getVisibility() == VISIBLE) {
            return;
        }
        if (mDelegate.mViewChangeListener != null) {
            mDelegate.mViewChangeListener.onViewChange(false);
        }
    }

    /**
     * 周视图显示事件
     */
    private void onShowMonthView() {
        if (mMonthView.getVisibility() == VISIBLE) {
            return;
        }
        if (mDelegate.mViewChangeListener != null) {
            mDelegate.mViewChangeListener.onViewChange(true);
        }
    }

    /**
     * ContentView是否滚动到顶部 如果完全不适合，就复写这个方法
     *
     * @return 是否滚动到顶部
     */
    protected boolean isScrollTop() {
        if (!isContentViewAlignedTop) {
            return true;
        }
        if (mContentView instanceof CalendarScrollView) {
            return ((CalendarScrollView) mContentView).isScrollToTop();
        }
        if (mContentView instanceof RecyclerView)
            return ((RecyclerView) mContentView).computeVerticalScrollOffset() == 0;
        if (mContentView instanceof AbsListView) {
            boolean result = false;
            AbsListView listView = (AbsListView) mContentView;
            if (listView.getFirstVisiblePosition() == 0) {
                final View topChildView = listView.getChildAt(0);
                result = topChildView.getTop() == 0;
            }
            return result;
        }
        return mContentView.getScrollY() == 0;
    }

    /**
     * 隐藏内容布局
     */
    @SuppressLint("NewApi")
    final void hideContentView() {
        if (mContentView == null)
            return;
        mContentView.animate()
                .translationY(getHeight() - mMonthView.getHeight())
                .setDuration(220)
                .setInterpolator(new LinearInterpolator())
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        super.onAnimationEnd(animation);
                        mContentView.setVisibility(INVISIBLE);
                        mContentView.clearAnimation();
                    }
                });
    }

    /**
     * 显示内容布局
     */
    @SuppressLint("NewApi")
    final void showContentView() {
        if (mContentView == null)
            return;
        translateContentView(getHeight() - mMonthView.getHeight());
        mContentView.setVisibility(VISIBLE);
        mContentView.animate()
                .translationY(0)
                .setDuration(180)
                .setInterpolator(new LinearInterpolator())
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        super.onAnimationEnd(animation);
                    }
                });
    }

    @SuppressWarnings("unused")
    private int getCalendarViewHeight() {
        return mMonthView.getVisibility() == VISIBLE ? mDelegate.getWeekBarHeight() + mMonthView.getHeight() :
                mDelegate.getWeekBarHeight() + mDelegate.getCalendarItemHeight();
    }

    /**
     * 如果有十分特别的ContentView，可以自定义实现这个接口
     */
    public interface CalendarScrollView {
        /**
         * 是否滚动到顶部
         *
         * @return 是否滚动到顶部
         */
        boolean isScrollToTop();
    }

    public interface ContentViewListener {
        /**
         * ContentView平移到周视图状态
         */
        void onTranslateToTop();
    }

    public void setContentViewListener(ContentViewListener listener) {
        mContentViewListener = listener;
    }

    private void onContentViewTranslateToTop() {
        if (mContentViewListener != null && isTouchDownContentView) {
            mContentViewListener.onTranslateToTop();
            isContentViewAlignedTop = true;
        }
    }

    public boolean isContentViewAlignedTop() {
        return isContentViewAlignedTop;
    }
}
