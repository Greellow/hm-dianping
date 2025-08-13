package com.hmdp.utils;

public interface ILock {
    public boolean tryLock(Long timeSec);

    public void unlock();
}
