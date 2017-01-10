package com.statictomato.apps.rovercontroller;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;


public class ControlStickView extends View {

    private static final int INVALID_POINTER_ID = -1;

    private ControlStickListener listener;

    private Drawable base;
    private Drawable hat;

    private int activePointer;

    private float centerX;
    private float centerY;

    private float hatRadius;
    private float maxOuterRadius;
    private float maxInnerRadius;

    private float hatCenterX;
    private float hatCenterY;

    private float lastTouchX;
    private float lastTouchY;

    public ControlStickView(Context context) {
        super(context);
        init(context,null, 0);
    }

    public ControlStickView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context,attrs, 0);
    }

    public ControlStickView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context,attrs, defStyle);
    }

    private void init(Context context, AttributeSet attrs, int defStyle) {
        /* Get context */
        if (context instanceof ControlStickListener) {
            listener = (ControlStickListener) context;
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement ControlStickListener");
        }

        /* No active pointer */
        activePointer = INVALID_POINTER_ID;

        /* Appearance setup */
        appearanceSetup();
    }

    public interface ControlStickListener {
        void onControlStickMoved(float percentX, float percentY, int source);
    }

    private void appearanceSetup() {
        Resources r = getResources();
        base = r.getDrawable(R.drawable.joystick_base);
        hat = r.getDrawable(R.drawable.joystick_hat);
    }

    private void dimensionSetup() {
        centerX = getWidth()/2;
        centerY = getHeight()/2;

        hatCenterX = centerX;
        hatCenterY = centerY;

        int max = Math.min(getWidth(),getHeight());
        float baseRadius = max/3;

        maxInnerRadius = max*3/10;
        maxOuterRadius = max/2;
        hatRadius = max/5;

        lastTouchX = centerX;
        lastTouchY = centerY;

        base.setBounds((int)(centerX - baseRadius), (int)(centerY - baseRadius),
                (int)(centerX + baseRadius), (int)(centerY + baseRadius));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        //TODO:
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        /* Not really needed */
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        /* Dimension setup */
        dimensionSetup();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        /* Draw control stick */
        base.draw(canvas);
        hat.setBounds((int)(hatCenterX - hatRadius),(int) (hatCenterY - hatRadius),
                (int)(hatCenterX + hatRadius),(int)(hatCenterY + hatRadius));
        hat.draw(canvas);
    }

    @Override
    public boolean onTouchEvent(MotionEvent motionEvent) {
        final int action = motionEvent.getActionMasked();
        switch(action) {
            case MotionEvent.ACTION_DOWN: {
                /* Get touch coordinates */
                float touchX = motionEvent.getX();
                float touchY = motionEvent.getY();
                /* Calculate relative displacement from center to touch point*/
                final float dTouch = (float) Math.sqrt(Math.pow(touchX - centerX,2) + Math.pow(touchY - centerY,2));
                if(dTouch < hatRadius) {
                    activePointer = motionEvent.getPointerId(0);
                    lastTouchX = touchX;
                    lastTouchY = touchY;
                }
                break;
            }

            case MotionEvent.ACTION_MOVE: {
                if(activePointer != INVALID_POINTER_ID) {
                    /* Get touch coordinates */
                    final int pIndex = motionEvent.findPointerIndex(activePointer);
                    final float touchX = motionEvent.getX(pIndex);
                    final float touchY = motionEvent.getY(pIndex);
                    /* Calculate relative displacement from center to touch point*/
                    final float dTouch = (float) Math.sqrt(Math.pow(touchX - centerX,2) + Math.pow(touchY - centerY,2));
                    /*if(dTouch > maxOuterRadius) { // If joystick is bound by maxOuterRadius
                        activePointer = INVALID_POINTER_ID;
                        hatCenterX = centerX;
                        hatCenterY = centerY;
                        *//* Execute callback: *//*
                        listener.onControlStickMoved(0,0,getId());
                        invalidate(); // Tells the runtime to call onDraw() in near future
                    }else {*/
                        /* Find X and Y displacement - used to move hat: */
                        hatCenterX += touchX - lastTouchX;
                        hatCenterY += touchY - lastTouchY;
                        /* Calculate relative displacement from center to hat*/
                        final float dHat = (float) Math.sqrt(Math.pow(hatCenterX - centerX,2) + Math.pow(hatCenterY - centerY,2));
                        if(dHat > maxInnerRadius) {
                            float ratio = maxInnerRadius / dHat;
                            hatCenterX = centerX + (hatCenterX - centerX) * ratio;
                            hatCenterY = centerY + (hatCenterY - centerY) * ratio;
                        }
                        /* Update last touch point */
                        lastTouchX = touchX;
                        lastTouchY = touchY;
                        if(dTouch > maxOuterRadius) { // If joystick is not bound by maxOuterRadius
                            float ratio = maxOuterRadius / dTouch;
                            lastTouchX = centerX + (touchX - centerX) * ratio;
                            lastTouchY = centerY + (touchY - centerY) * ratio;
                        }
                        /* Execute callback: */
                        listener.onControlStickMoved((hatCenterX - centerX)/maxInnerRadius,(hatCenterY - centerY)/maxInnerRadius,getId());
                        invalidate(); // Tells the runtime to call onDraw() in near future
                    //}
                } else {
                    final int pCount = motionEvent.getPointerCount();
                    /* Get touch coordinates */
                    float touchX;
                    float touchY;
                    /* Calculate relative displacement from center to touch point*/
                    float dTouch;
                    for(int n = 0; n < pCount; ++n) {
                        /* Get touch coordinates */
                        touchX = motionEvent.getX(n);
                        touchY = motionEvent.getY(n);
                        /* Calculate relative displacement from center to touch point*/
                        dTouch = (float) Math.sqrt(Math.pow(touchX - centerX,2) + Math.pow(touchY - centerY,2));
                        if(dTouch < hatRadius) {
                            activePointer = motionEvent.getPointerId(n);
                            lastTouchX = touchX;
                            lastTouchY = touchY;
                        }
                    }
                }
                break;
            }

            case MotionEvent.ACTION_UP: {
                if(activePointer != INVALID_POINTER_ID) {
                    activePointer = INVALID_POINTER_ID;
                    hatCenterX = centerX;
                    hatCenterY = centerY;
                    listener.onControlStickMoved(0,0,getId());
                    invalidate(); // Tells the runtime to call onDraw() in near future
                }
                break;
            }

            case MotionEvent.ACTION_POINTER_UP: {
                if(activePointer != INVALID_POINTER_ID) {
                    /* Extract the index of the pointer that enter touch sensor */
                    final int pIndex = motionEvent.getActionIndex();
                    final int pId = motionEvent.getPointerId(pIndex);
                    if(activePointer == pId) {
                        activePointer = INVALID_POINTER_ID;
                        hatCenterX = centerX;
                        hatCenterY = centerY;
                        listener.onControlStickMoved(0,0,getId());
                        invalidate(); // Tells the runtime to call onDraw() in near future
                    }
                }
                break;
            }

            case MotionEvent.ACTION_CANCEL: {
                if(activePointer != INVALID_POINTER_ID) {
                    /* Extract the index of the pointer that enter touch sensor */
                    final int pIndex = motionEvent.getActionIndex();
                    final int pId = motionEvent.getPointerId(pIndex);
                    if(activePointer == pId) {
                        activePointer = INVALID_POINTER_ID;
                        hatCenterX = centerX;
                        hatCenterY = centerY;
                        listener.onControlStickMoved(0,0,getId());
                        invalidate(); // Tells the runtime to call onDraw() in near future
                    }
                }
                break;
            }
        }

        return true;
    }
}
