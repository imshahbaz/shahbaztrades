package com.app.shahbaztrades.service;

import java.util.concurrent.ExecutionException;

public interface SessionManagerService {

    void initiateZerodhaLogin() throws ExecutionException, InterruptedException;
}
