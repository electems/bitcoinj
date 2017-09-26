package com.example;

import static org.bitcoinj.testing.FakeTxBuilder.createFakeBlock;

import javax.annotation.Nullable;

import org.bitcoinj.core.AbstractBlockChain;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Block;
import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.VerificationException;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.MemoryBlockStore;
import org.bitcoinj.testing.FakeTxBuilder;
import org.bitcoinj.utils.BriefLogFormatter;
import org.bitcoinj.wallet.Wallet;

public class WalletParams {
    protected static final NetworkParameters PARAMS = TestNet3Params.get();
    protected ECKey myKey;
    protected Address myAddress;
    protected Wallet wallet;
    protected BlockChain chain;
    protected BlockStore blockStore;

    public void setUp() throws Exception {
        BriefLogFormatter.init();
        Context.propagate(new Context(PARAMS, 100, Coin.ZERO, false));
        wallet = new Wallet(PARAMS);
        myKey = wallet.currentReceiveKey();
        myAddress = myKey.toAddress(PARAMS);
        blockStore = new MemoryBlockStore(PARAMS);
        chain = new BlockChain(PARAMS, wallet, blockStore);
    }

    public void tearDown() throws Exception {
    }

    @Nullable
    protected Transaction sendMoneyToWallet(Wallet wallet, AbstractBlockChain.NewBlockType type, Transaction... transactions)
            throws VerificationException {
        if (type == null) {
            // Pending transaction
            for (Transaction tx : transactions)
                if (wallet.isPendingTransactionRelevant(tx))
                    wallet.receivePending(tx, null);
        } else {
            FakeTxBuilder.BlockPair bp = createFakeBlock(blockStore, Block.BLOCK_HEIGHT_GENESIS, transactions);
            for (Transaction tx : transactions)
                wallet.receiveFromBlock(tx, bp.storedBlock, type, 0);
            if (type == AbstractBlockChain.NewBlockType.BEST_CHAIN)
                wallet.notifyNewBestBlock(bp.storedBlock);
        }
        if (transactions.length == 1)
            return wallet.getTransaction(transactions[0].getHash());  // Can be null if tx is a double spend that's otherwise irrelevant.
        else
            return null;
    }
}
