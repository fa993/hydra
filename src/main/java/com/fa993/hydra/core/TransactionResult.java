package com.fa993.hydra.core;

/**
 *
 * Use COMMAND if the transaction was successful and the object is a {@link Command} object
 * Use STATE if the transaction was successful and the object is a {@link State} object
 * Use FAILURE if the transaction could not be completed
 * Use VETOED if the transaction was vetoed (it could happen as a resolution to a split-brain issue)
 * USE TIMEOUT if the transaction was cancelled due to a timeout
 *
 */
public enum TransactionResult {

    COMMAND, STATE, FAILURE, VETOED, TIMEOUT

}
