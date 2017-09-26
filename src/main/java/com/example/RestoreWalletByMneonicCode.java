package com.example;

import static org.bitcoinj.core.Coin.COIN;
import static org.bitcoinj.core.Coin.valueOf;
import static org.bitcoinj.testing.FakeTxBuilder.createFakeTx;

import java.io.File;
import java.security.SecureRandom;
import java.util.List;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.Utils;
import org.bitcoinj.core.listeners.DownloadProgressTracker;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.net.discovery.DnsDiscovery;
import org.bitcoinj.store.MemoryBlockStore;
import org.bitcoinj.store.SPVBlockStore;
import org.bitcoinj.testing.KeyChainTransactionSigner;
import org.bitcoinj.wallet.DeterministicKeyChain;
import org.bitcoinj.wallet.DeterministicSeed;
import org.bitcoinj.wallet.MarriedKeyChain;
import org.bitcoinj.wallet.Wallet;
import org.junit.After;
import org.junit.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

//Following Class has logic to Restore the Wallet By using MneonicCode which is created while backup of Wallet
public class RestoreWalletByMneonicCode extends WalletParams {
	
	 private static final Logger log = LoggerFactory.getLogger(RestoreWalletByMneonicCode.class);
	    
	    private static String mnemonicCode = "";	    
	    private static Long creationDate = null;

	    private final Address OTHER_ADDRESS = new ECKey().toAddress(PARAMS);
	    
	    @Before
	    @Override
	    public void setUp() throws Exception {
	        super.setUp();
	    }

	    @After
	    @Override
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
	        
	        blockStore = new MemoryBlockStore(PARAMS);
	        chain = new BlockChain(PARAMS, wallet, blockStore);

	        List<DeterministicKey> followingKeys = Lists.newArrayList();
	        for (int i = 0; i < numKeys - 1; i++) {
	            final DeterministicKeyChain keyChain = new DeterministicKeyChain(new SecureRandom());
	            DeterministicKey partnerKey = DeterministicKey.deserializeB58(null, keyChain.getWatchingKey().serializePubB58(PARAMS), PARAMS);
	            followingKeys.add(partnerKey);
	            if (addSigners && i < threshold - 1)
	                wallet.addTransactionSigner(new KeyChainTransactionSigner(keyChain));
	        }

	        MarriedKeyChain chain = MarriedKeyChain.builder()
	                .random(new SecureRandom())
	                .followingKeys(followingKeys)
	                .threshold(threshold).build();
	        wallet.addAndActivateHDChain(chain);        
	        
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

	        // Now we need to hook the wallet up to the blockchain and the peers. This registers event listeners that notify our wallet about new transactions.
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
