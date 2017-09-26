package com.example;

import java.util.LinkedList;

import javax.annotation.Nullable;

import static com.example.FakeTxBuilder.createFakeBlock;
import static com.example.FakeTxBuilder.createFakeTx;
import static org.bitcoinj.core.Coin.COIN;
import static org.bitcoinj.core.Coin.valueOf;

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
import org.bitcoinj.core.listeners.TransactionConfidenceEventListener;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.MemoryBlockStore;
import org.bitcoinj.utils.BriefLogFormatter;
import org.bitcoinj.utils.Threading;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.listeners.WalletCoinsReceivedEventListener;
import org.bitcoinj.wallet.listeners.WalletCoinsSentEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Component
public class TransactionBtwWallets implements ApplicationListener<ApplicationReadyEvent> {

	private static final Logger log = LoggerFactory.getLogger(TransactionBtwWallets.class);
	private final Address OTHER_ADDRESS = new ECKey().toAddress(PARAMS);

	final Coin[] bigints = new Coin[4];
	final Transaction[] txn = new Transaction[2];
	final LinkedList<Transaction> confTxns = new LinkedList<Transaction>();

	protected static final NetworkParameters PARAMS = UnitTestParams.get();
    protected ECKey myKey;
    protected Address myAddress;
    protected Wallet wallet;
    protected BlockChain chain;
    protected BlockStore blockStore;
	
	@Override
	public void onApplicationEvent(ApplicationReadyEvent event) {
		try {
			setUp();
			tearDown();
			transactionFromOneWalletToOther();
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}

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
    
	
	/*
	 * Following block of code is used to make a transaction between wallets.
	 */
	public void transactionFromOneWalletToOther() throws Exception {
		
        wallet.addCoinsReceivedEventListener(new WalletCoinsReceivedEventListener() {
            @Override
            public void onCoinsReceived(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
                bigints[0] = prevBalance;
                bigints[1] = newBalance;
                txn[0] = tx;
            }
        });

        wallet.addCoinsSentEventListener(new WalletCoinsSentEventListener() {
            @Override
            public void onCoinsSent(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
                bigints[2] = prevBalance;
                bigints[3] = newBalance;
                txn[1] = tx;
            }
        });

        wallet.addTransactionConfidenceEventListener(new TransactionConfidenceEventListener() {
            @Override
            public void onTransactionConfidenceChanged(Wallet wallet, Transaction tx) {
                confTxns.add(tx);
            }
        });

        // add some bit coins for transaction.
        Coin nanos = COIN;
        Transaction mywallet = sendMoneyToWallet(AbstractBlockChain.NewBlockType.BEST_CHAIN, nanos);
        Coin totalCoinsInMyWallet =  mywallet.getValueSentToMe(wallet);
        log.info("Total Bitcoins in my wallets : "+ totalCoinsInMyWallet); 
       
       if(mywallet.getWalletOutputs(wallet).size() >= 1){
        
        //send money from myWallet to others wallet via address.
        Transaction firstTransaction = wallet.createSend(OTHER_ADDRESS, valueOf(0, 15));       
           txn[0] = txn[1] = null;
           confTxns.clear();
           sendMoneyToWallet(AbstractBlockChain.NewBlockType.BEST_CHAIN, firstTransaction);
           Threading.waitForUserCode();
           log.info("Total Bitcoins remains in my wallets after first transaction : "+ wallet.getBalance());
           
           // we send again after the firstTransaction.
           Transaction secondTransaction = wallet.createSend(OTHER_ADDRESS, valueOf(0, 8));       
           
           wallet.commitTx(secondTransaction); //it will update flag on spent coins.
           sendMoneyToWallet(AbstractBlockChain.NewBlockType.BEST_CHAIN, secondTransaction);           
           Threading.waitForUserCode();  
           log.info("Total Bitcoins remains in my wallets after second transaction : "+ wallet.getBalance()); 
           confTxns.clear();
       }        
    }

	 @Nullable
	    protected Transaction sendMoneyToWallet(Wallet wallet, AbstractBlockChain.NewBlockType type, Coin value, Address toAddress) throws VerificationException {
	        return sendMoneyToWallet(wallet, type, createFakeTx(PARAMS, value, toAddress));
	    }

	    @Nullable
	    protected Transaction sendMoneyToWallet(Wallet wallet, AbstractBlockChain.NewBlockType type, Coin value, ECKey toPubKey) throws VerificationException {
	        return sendMoneyToWallet(wallet, type, createFakeTx(PARAMS, value, toPubKey));
	    }

	    @Nullable
	    protected Transaction sendMoneyToWallet(AbstractBlockChain.NewBlockType type, Transaction... transactions) throws VerificationException {
	        return sendMoneyToWallet(this.wallet, type, transactions);
	    }

	    @Nullable
	    protected Transaction sendMoneyToWallet(AbstractBlockChain.NewBlockType type, Coin value) throws VerificationException {
	        return sendMoneyToWallet(this.wallet, type, value, myAddress);
	    }

	    @Nullable
	    protected Transaction sendMoneyToWallet(AbstractBlockChain.NewBlockType type, Coin value, Address toAddress) throws VerificationException {
	        return sendMoneyToWallet(this.wallet, type, value, toAddress);
	    }

	    @Nullable
	    protected Transaction sendMoneyToWallet(AbstractBlockChain.NewBlockType type, Coin value, ECKey toPubKey) throws VerificationException {
	        return sendMoneyToWallet(this.wallet, type, value, toPubKey);
	    }
}
