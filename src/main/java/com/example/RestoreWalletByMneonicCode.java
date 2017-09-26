package com.example;

import static com.example.FakeTxBuilder.createFakeTx;
import static org.bitcoinj.core.Coin.COIN;
import static org.bitcoinj.core.Coin.valueOf;

import java.io.File;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.Utils;
import org.bitcoinj.core.listeners.DownloadProgressTracker;
import org.bitcoinj.net.discovery.DnsDiscovery;
import org.bitcoinj.store.SPVBlockStore;
import org.bitcoinj.wallet.DeterministicSeed;
import org.bitcoinj.wallet.Wallet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

//Following Class has logic to Restore the Wallet By using MneonicCode which is created while backup of Wallet
@Component
public class RestoreWalletByMneonicCode extends WalletParams implements ApplicationListener<ApplicationReadyEvent> {
	
	 private static final Logger log = LoggerFactory.getLogger(RestoreWalletByMneonicCode.class);
	 
	 @Override
		public void onApplicationEvent(ApplicationReadyEvent event) {
		 	try {
		 		setUp();
		 		tearDown();
				transactions();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	    
	    private static String mnemonicCode = "";	    
	    private static Long creationDate = null;

	    private final Address OTHER_ADDRESS = new ECKey().toAddress(PARAMS);
	    
	    
	    public void setUp() throws Exception {
	        super.setUp();
	    }
	    
	    public void tearDown() throws Exception {
	        super.tearDown();
	    }
	    
	    private void createMarriedWallet(int threshold, int numKeys) throws Exception {
	        createWallet(threshold, numKeys, true);
	    }

	    private void createWallet(int threshold, int numKeys, boolean addSigners) throws Exception {
	        wallet = new Wallet(PARAMS);
	        
	        //Following Code creates backup of wallet in order to restore it in future.
	        DeterministicSeed seed = wallet.getKeyChainSeed();
	        mnemonicCode = Utils.join(seed.getMnemonicCode());
	        creationDate = seed.getCreationTimeSeconds();
	        
	        System.out.println("mnemonicCode: " + Utils.join(seed.getMnemonicCode()));
	        System.out.println("creation time: " + seed.getCreationTimeSeconds());      
	           
	        
	    }
	    
	    /*
	     * Following Function Creates transaction and adds bit coin to the created wallet.
	     */
	    public void transactions() throws Exception {
	    	createMarriedWallet(2, 2);
	    	
	        // This test covers a bug in which Transaction.getValueSentFromMe was calculating incorrectly.
	        Transaction tx = createFakeTx(PARAMS, COIN, myAddress);
	        
	        // Now add another output (ie, change) that goes to some other address.
	        TransactionOutput output = new TransactionOutput(PARAMS, tx, valueOf(0, 5), OTHER_ADDRESS);
	        tx.addOutput(output);     
	        
	        resotreWalletFromMnemonic();
	        
	    }
	    
	    /*
	     * Following Function restores the wallet using mnemonicCode.
	     */
	    public void resotreWalletFromMnemonic() throws Exception {
	    	String seedCode = mnemonicCode;
	        String passphrase = "";
	        Long creationtime = creationDate;

	        DeterministicSeed seed = new DeterministicSeed(seedCode, null, passphrase, creationtime);
	        
	     // The wallet class provides a easy fromSeed() function that loads a new wallet from a given seed.
	        Wallet wallet = Wallet.fromSeed(PARAMS, seed);
	        
	        System.out.println(wallet.toString());
	        
	        wallet.clearTransactions(0);
	        File chainFile = new File("restore-from-seed.spvchain");
	        if (chainFile.exists()) {
	            chainFile.delete();
	        }

	        // Setting up the BlochChain, the BlocksStore and connecting to the network.
	        SPVBlockStore chainStore = new SPVBlockStore(PARAMS, chainFile);
	        BlockChain chain = new BlockChain(PARAMS, chainStore);
	        PeerGroup peers = new PeerGroup(PARAMS, chain);
	        peers.addPeerDiscovery(new DnsDiscovery(PARAMS));

	        // This registers event listeners that notify our wallet about new transactions.
	        chain.addWallet(wallet);
	        peers.addWallet(wallet);

	        DownloadProgressTracker bListener = new DownloadProgressTracker() {
	            @Override
	            public void doneDownload() {
	                System.out.println("blockchain downloaded");
	            }
	        };

	        // Now we re-download the blockchain. Once this is completed our wallet should print the correct balance.
	        peers.start();
	        peers.startBlockChainDownload(bListener);

	        bListener.await();

	        // The correct balance should now be displayed.
	        System.out.println(wallet.toString());

	        // shutting down again
	        peers.stop();
	    }	
	
}
