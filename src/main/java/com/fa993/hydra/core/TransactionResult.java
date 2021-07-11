package com.fa993.hydra.core;

/**
 *
 * Use SUCCESS if the transaction was successful
 * Use FAILURE if the transaction could not be completed
 * Use VETOED if the transaction was vetoed (it could happen as a resolution to a split-brain issue)
 * USE TIMEOUT if the transaction was cancelled due to a timeout
 *
 */
public enum TransactionResult {

    SUCCESS, FAILURE, VETOED, TIMEOUT

}
