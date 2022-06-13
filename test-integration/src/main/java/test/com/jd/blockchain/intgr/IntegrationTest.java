package test.com.jd.blockchain.intgr;

import static com.jd.blockchain.transaction.TxBuilder.computeTxContentHash;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.doAnswer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.core.io.ClassPathResource;

import com.jd.binaryproto.BinaryProtocol;
import com.jd.blockchain.consensus.ConsensusProvider;
import com.jd.blockchain.consensus.ConsensusProviders;
import com.jd.blockchain.consensus.ConsensusViewSettings;
import com.jd.blockchain.consensus.bftsmart.BftsmartConsensusProvider;
import com.jd.blockchain.consensus.bftsmart.service.BftsmartNodeServer;
import com.jd.blockchain.consensus.bftsmart.service.BftsmartServerSettings;
import com.jd.blockchain.consensus.service.MessageHandle;
import com.jd.blockchain.consensus.service.NodeServerFactory;
import com.jd.blockchain.consensus.service.StateMachineReplicate;
import com.jd.blockchain.crypto.AddressEncoding;
import com.jd.blockchain.crypto.AsymmetricKeypair;
import com.jd.blockchain.crypto.Crypto;
import com.jd.blockchain.crypto.HashDigest;
import com.jd.blockchain.crypto.KeyGenUtils;
import com.jd.blockchain.crypto.PrivKey;
import com.jd.blockchain.crypto.PubKey;
import com.jd.blockchain.gateway.GatewayConfigProperties.KeyPairConfig;
import com.jd.blockchain.ledger.BlockchainIdentity;
import com.jd.blockchain.ledger.BlockchainKeyGenerator;
import com.jd.blockchain.ledger.BlockchainKeypair;
import com.jd.blockchain.ledger.BytesValue;
import com.jd.blockchain.ledger.DataAccountKVSetOperation;
import com.jd.blockchain.ledger.LedgerBlock;
import com.jd.blockchain.ledger.LedgerInfo;
import com.jd.blockchain.ledger.LedgerInitProperties;
import com.jd.blockchain.ledger.ParticipantNode;
import com.jd.blockchain.ledger.PreparedTransaction;
import com.jd.blockchain.ledger.TransactionRequest;
import com.jd.blockchain.ledger.TransactionResponse;
import com.jd.blockchain.ledger.TransactionTemplate;
import com.jd.blockchain.ledger.TypedKVEntry;
import com.jd.blockchain.ledger.UserInfo;
import com.jd.blockchain.ledger.core.DataAccountSet;
import com.jd.blockchain.ledger.core.DefaultOperationHandleRegisteration;
import com.jd.blockchain.ledger.core.LedgerManage;
import com.jd.blockchain.ledger.core.LedgerManager;
import com.jd.blockchain.ledger.core.LedgerQuery;
import com.jd.blockchain.ledger.core.LedgerRepository;
import com.jd.blockchain.ledger.core.TransactionEngineImpl;
import com.jd.blockchain.peer.consensus.ConsensusMessageDispatcher;
import com.jd.blockchain.peer.consensus.LedgerStateManager;
import com.jd.blockchain.sdk.BlockchainService;
import com.jd.blockchain.sdk.client.GatewayServiceFactory;
import com.jd.blockchain.storage.service.KVStorageService;
import com.jd.blockchain.storage.service.impl.composite.CompositeConnectionFactory;
import com.jd.blockchain.test.PeerServer;
import com.jd.blockchain.tools.initializer.DBConnectionConfig;
import com.jd.blockchain.tools.initializer.LedgerBindingConfig;
import com.jd.blockchain.tools.initializer.Prompter;
import com.jd.blockchain.transaction.TxContentBlob;
import com.jd.blockchain.transaction.TxRequestBuilder;
import com.jd.blockchain.transaction.UserRegisterOpTemplate;

import test.com.jd.blockchain.intgr.IntegratedContext.Node;
import test.com.jd.blockchain.intgr.perf.LedgerInitializeWebTest;
import test.com.jd.blockchain.intgr.perf.Utils;
import utils.Bytes;
import utils.codec.HexUtils;
import utils.concurrent.AsyncFuture;
import utils.concurrent.ThreadInvoker.AsyncCallback;
import utils.io.Storage;
import utils.net.NetworkAddress;

public class IntegrationTest {
	// 合约测试使用的初始化数据;
	BlockchainKeypair contractDataKey = BlockchainKeyGenerator.getInstance().generate();
	BlockchainKeypair contractDeployKey = BlockchainKeyGenerator.getInstance().generate();
	private String contractZipName = "AssetContract1.contract";
	private String eventName = "issue-asset";
	HashDigest txContentHash;
	// String userPubKeyVal = "this is user's pubKey";
	// 保存资产总数的键；
	private static final String KEY_TOTAL = "TOTAL";
	// 第二个参数;
	private static final String KEY_ABC = "abc";

	private static final String MQ_SERVER = "nats://127.0.0.1:4222";

	private static final String MQ_TOPIC = "subject";

	private static String memDbConnString = LedgerInitConsensusConfig.memConnectionStrings[0];

	private static AtomicBoolean isTestTurnOn0 = new AtomicBoolean(false);

	private static AtomicBoolean isTestTurnOn1 = new AtomicBoolean(false);

	private static AtomicBoolean isTestTurnOn2 = new AtomicBoolean(false);

	private static AtomicBoolean isTestTurnOn3 = new AtomicBoolean(false);

	public static final String PASSWORD = "abc";

	public static final String PUB_KEYS = "3snPdw7i7Pb3B5AxpSXy6YVruvftugNQ7rB7k2KWukhBwKQhFBFagT";
	public static final String PRIV_KEYS = "177gjtSgSdUF3LwRFGhzbpZZxmXXChsnwbuuLCG1V9KYfVuuxLwXGmZCp5FGUvsenhwBQLV";

	public static void main_(String[] args) {
		// init ledgers of all nodes ;
		IntegratedContext context = initLedgers();

		Node node0 = context.getNode(0);
		Node node1 = context.getNode(1);
		Node node2 = context.getNode(2);
		Node node3 = context.getNode(3);

		// mock ledger manager
		LedgerManager ledgerManagerMock0 = Mockito.spy(node0.getLedgerManager());
		LedgerManager ledgerManagerMock1 = Mockito.spy(node1.getLedgerManager());
		LedgerManager ledgerManagerMock2 = Mockito.spy(node2.getLedgerManager());
		LedgerManager ledgerManagerMock3 = Mockito.spy(node3.getLedgerManager());

		node0.setLedgerManager(ledgerManagerMock0);
		node1.setLedgerManager(ledgerManagerMock1);
		node2.setLedgerManager(ledgerManagerMock2);
		node3.setLedgerManager(ledgerManagerMock3);

		// mock noder server
		mockNodeServer(context);

		NetworkAddress peerSrvAddr0 = new NetworkAddress("127.0.0.1", 10200);
		PeerServer peer0 = new PeerServer(peerSrvAddr0, node0.getBindingConfig(), node0.getStorageDB(),
				node0.getLedgerManager());

		NetworkAddress peerSrvAddr1 = new NetworkAddress("127.0.0.1", 10210);
		PeerServer peer1 = new PeerServer(peerSrvAddr1, node1.getBindingConfig(), node1.getStorageDB(),
				node1.getLedgerManager());

		NetworkAddress peerSrvAddr2 = new NetworkAddress("127.0.0.1", 10220);
		PeerServer peer2 = new PeerServer(peerSrvAddr2, node2.getBindingConfig(), node2.getStorageDB(),
				node2.getLedgerManager());

		NetworkAddress peerSrvAddr3 = new NetworkAddress("127.0.0.1", 10230);
		PeerServer peer3 = new PeerServer(peerSrvAddr3, node3.getBindingConfig(), node3.getStorageDB(),
				node3.getLedgerManager());

		AsyncCallback<Object> peerStarting0 = peer0.start();
		AsyncCallback<Object> peerStarting1 = peer1.start();
		AsyncCallback<Object> peerStarting2 = peer2.start();
		AsyncCallback<Object> peerStarting3 = peer3.start();

		peerStarting0.waitReturn();
		peerStarting1.waitReturn();
		peerStarting2.waitReturn();
		peerStarting3.waitReturn();

		String encodedBase58Pwd = KeyGenUtils.encodePasswordAsBase58(LedgerInitializeWebTest.PASSWORD);

		KeyPairConfig gwkey0 = new KeyPairConfig();
		gwkey0.setPubKeyValue(LedgerInitializeWebTest.PUB_KEYS[0]);
		gwkey0.setPrivKeyValue(LedgerInitializeWebTest.PRIV_KEYS[0]);
		gwkey0.setPrivKeyPassword(encodedBase58Pwd);

		GatewayTestRunner gateway0 = new GatewayTestRunner("127.0.0.1", 10300, gwkey0,
				LedgerInitConsensusConfig.bftsmartProvider, null, peerSrvAddr0);

		AsyncCallback<Object> gwStarting0 = gateway0.start();

		gwStarting0.waitReturn();

		// 执行测试用例之前，校验每个节点的一致性；
//		testConsistencyAmongNodes(context);

		testStorageErrorBlockRollbackSdk(gateway0, context);

		testConsensusFirstTimeoutSdk(gateway0, context);

		testConsensusSecondTimeoutSdk(gateway0, context);

		testTransactionRollbackSdk(gateway0, context);

		testInvalidUserSignerSdk(gateway0, context);

		testBlockHashTwoInconsistentSdk(gateway0, context);

		testBlockHashFourInconsistentSdk(gateway0, context);

		testSDK(gateway0, context);

		System.out.println("------IntegrationTest Ok--------");

		// 执行测试用例之后，校验每个节点的一致性；
//		 testConsistencyAmongNodes(context);
	}

	public static void createNewTx(InvocationOnMock invocation, short hashAlgorithm, PubKey nodePubKey,
			PrivKey nodePrivKey) {

		byte[] origTxBytes = (byte[]) invocation.getArguments()[1];

		TransactionRequest origTx = BinaryProtocol.decode(origTxBytes);

		// Generate new tx
		BlockchainKeypair user = BlockchainKeyGenerator.getInstance().generate();

		UserRegisterOpTemplate usrOperation = new UserRegisterOpTemplate(user.getIdentity());

		TxContentBlob txContent = new TxContentBlob(origTx.getTransactionContent().getLedgerHash());

		txContent.addOperation(usrOperation);

		txContent.setTime(System.currentTimeMillis());

		HashDigest contentHash = computeTxContentHash(hashAlgorithm, txContent);

		TxRequestBuilder txRequestBuilder = new TxRequestBuilder(contentHash, txContent);

		AsymmetricKeypair keyPair = new AsymmetricKeypair(nodePubKey, nodePrivKey);

		txRequestBuilder.signAsEndpoint(keyPair);

		txRequestBuilder.signAsNode(keyPair);

		TransactionRequest newTx = txRequestBuilder.buildRequest();

		invocation.getArguments()[1] = BinaryProtocol.encode(newTx, TransactionRequest.class);

	}

	// todo
	private static void mockNodeServer(IntegratedContext context) {

		PubKey nodePubKey0 = context.getNode(0).getPartiKeyPair().getPubKey();
		PubKey nodePubKey1 = context.getNode(1).getPartiKeyPair().getPubKey();
		PubKey nodePubKey2 = context.getNode(2).getPartiKeyPair().getPubKey();
		PubKey nodePubKey3 = context.getNode(3).getPartiKeyPair().getPubKey();

		PrivKey nodePrivKey0 = context.getNode(0).getPartiKeyPair().getPrivKey();

		BftsmartConsensusProvider bftsmartProvider0 = BftsmartConsensusProvider.INSTANCE;
		BftsmartConsensusProvider mockedBftsmartProvider0 = Mockito.spy(bftsmartProvider0);

		ConsensusProviders.registerProvider(mockedBftsmartProvider0);

		doAnswer(new Answer<NodeServerFactory>() {
			@Override
			public NodeServerFactory answer(InvocationOnMock invocation) throws Throwable {
				NodeServerFactory nodeServerFactory = (NodeServerFactory) invocation.callRealMethod();
				NodeServerFactory mockedServerFactory = Mockito.spy(nodeServerFactory);

				doAnswer(new Answer<BftsmartNodeServer>() {
					@Override
					public BftsmartNodeServer answer(InvocationOnMock invocation) throws Throwable {
						BftsmartServerSettings serverSettings = (BftsmartServerSettings) invocation.getArguments()[0];

						MessageHandle messageHandle = new ConsensusMessageDispatcher();

						if (nodePubKey0.equals(serverSettings.getReplicaSettings().getPubKey())) {
							((ConsensusMessageDispatcher) messageHandle).setTxEngine(new TransactionEngineImpl(
									context.getNode(0).getLedgerManager(), new DefaultOperationHandleRegisteration()));
						} else if (nodePubKey1.equals(serverSettings.getReplicaSettings().getPubKey())) {
							((ConsensusMessageDispatcher) messageHandle).setTxEngine(new TransactionEngineImpl(
									context.getNode(1).getLedgerManager(), new DefaultOperationHandleRegisteration()));
						} else if (nodePubKey2.equals(serverSettings.getReplicaSettings().getPubKey())) {
							((ConsensusMessageDispatcher) messageHandle).setTxEngine(new TransactionEngineImpl(
									context.getNode(2).getLedgerManager(), new DefaultOperationHandleRegisteration()));
						} else if (nodePubKey3.equals(serverSettings.getReplicaSettings().getPubKey())) {
							((ConsensusMessageDispatcher) messageHandle).setTxEngine(new TransactionEngineImpl(
									context.getNode(3).getLedgerManager(), new DefaultOperationHandleRegisteration()));
						}

						// mock spy messageHandle
						MessageHandle mockedMessageHandle = Mockito.spy(messageHandle);

						StateMachineReplicate stateMachineReplicate = new LedgerStateManager();

						if (nodePubKey0.equals(serverSettings.getReplicaSettings().getPubKey())) {

							doAnswer(new Answer<AsyncFuture<byte[]>>() {
								@Override
								public AsyncFuture<byte[]> answer(InvocationOnMock invocation) throws Throwable {

									if (isTestTurnOn0.get()) {
										createNewTx(invocation, context.getCryptoSetting().getHashAlgorithm(),
												nodePubKey0, nodePrivKey0);
									}
									return (AsyncFuture<byte[]>) invocation.callRealMethod();
								}
							}).when(mockedMessageHandle).processOrdered(anyInt(), any(), any());
						} else if (nodePubKey1.equals(serverSettings.getReplicaSettings().getPubKey())) {

							doAnswer(new Answer<AsyncFuture<byte[]>>() {
								@Override
								public AsyncFuture<byte[]> answer(InvocationOnMock invocation) throws Throwable {

									if (isTestTurnOn1.get()) {
										createNewTx(invocation, context.getCryptoSetting().getHashAlgorithm(),
												nodePubKey0, nodePrivKey0);
									}
									return (AsyncFuture<byte[]>) invocation.callRealMethod();
								}
							}).when(mockedMessageHandle).processOrdered(anyInt(), any(), any());
						} else if (nodePubKey2.equals(serverSettings.getReplicaSettings().getPubKey())) {

							doAnswer(new Answer<AsyncFuture<byte[]>>() {
								@Override
								public AsyncFuture<byte[]> answer(InvocationOnMock invocation) throws Throwable {

									if (isTestTurnOn2.get()) {
										createNewTx(invocation, context.getCryptoSetting().getHashAlgorithm(),
												nodePubKey0, nodePrivKey0);
									}
									return (AsyncFuture<byte[]>) invocation.callRealMethod();
								}
							}).when(mockedMessageHandle).processOrdered(anyInt(), any(), any());
						} else if (nodePubKey3.equals(serverSettings.getReplicaSettings().getPubKey())) {

							doAnswer(new Answer<AsyncFuture<byte[]>>() {
								@Override
								public AsyncFuture<byte[]> answer(InvocationOnMock invocation) throws Throwable {

									if (isTestTurnOn3.get()) {
										createNewTx(invocation, context.getCryptoSetting().getHashAlgorithm(),
												nodePubKey0, nodePrivKey0);
									}
									return (AsyncFuture<byte[]>) invocation.callRealMethod();
								}
							}).when(mockedMessageHandle).processOrdered(anyInt(), any(), any());
						}

						Storage storage = invocation.getArgument(3, Storage.class);
						return new BftsmartNodeServer(serverSettings, mockedMessageHandle, stateMachineReplicate,
								storage);
					}
				}).when(mockedServerFactory).setupServer(any(), any(), any(), any());

				return mockedServerFactory;
			}
		}).when(mockedBftsmartProvider0).getServerFactory();
	}

	/**
	 * 检查所有节点之间的账本是否一致；
	 * 
	 * @param context
	 */
	private static void testConsistencyAmongNodes(IntegratedContext context) {
		int[] ids = context.getNodeIds();
		Node[] nodes = new Node[ids.length];
		LedgerQuery[] ledgers = new LedgerQuery[ids.length];
		for (int i = 0; i < nodes.length; i++) {
			nodes[i] = context.getNode(ids[i]);
			HashDigest ledgerHash = nodes[i].getLedgerManager().getLedgerHashs()[0];
			ledgers[i] = nodes[i].getLedgerManager().getLedger(ledgerHash);
		}
		LedgerQuery ledger0 = ledgers[0];
		LedgerBlock latestBlock0 = ledger0.retrieveLatestBlock();
		for (int i = 1; i < ledgers.length; i++) {
			LedgerQuery otherLedger = ledgers[i];
			LedgerBlock otherLatestBlock = otherLedger.retrieveLatestBlock();
		}
	}

	private static void testConsensusFirstTimeoutSdk(GatewayTestRunner gateway, IntegratedContext context) {

		AtomicBoolean isTestTurnOn = new AtomicBoolean(true);

		Node node0 = context.getNode(0);
		Node node1 = context.getNode(1);

		LedgerManager ledgerManagerMock0 = node0.getLedgerManager();
		LedgerManager ledgerManagerMock1 = node1.getLedgerManager();

		doAnswer(new Answer<LedgerRepository>() {
			@Override
			public LedgerRepository answer(InvocationOnMock invocation) throws Throwable {
				if (isTestTurnOn.get()) {
					Thread.sleep(5000);
				}
				return (LedgerRepository) invocation.callRealMethod();
			}
		}).when(ledgerManagerMock0).getLedger(any());

		doAnswer(new Answer<LedgerRepository>() {
			@Override
			public LedgerRepository answer(InvocationOnMock invocation) throws Throwable {
				if (isTestTurnOn.get()) {
					Thread.sleep(5000);
				}
				return (LedgerRepository) invocation.callRealMethod();
			}
		}).when(ledgerManagerMock1).getLedger(any());

		testSDK(gateway, context);

		isTestTurnOn.set(false);
	}

	private static void testConsensusSecondTimeoutSdk(GatewayTestRunner gateway, IntegratedContext context) {
		AtomicBoolean isTestTurnOn = new AtomicBoolean(true);

		Node node0 = context.getNode(0);
		Node node1 = context.getNode(1);

		LedgerManager ledgerManagerMock0 = node0.getLedgerManager();
		LedgerManager ledgerManagerMock1 = node1.getLedgerManager();

		doAnswer(new Answer<LedgerRepository>() {
			@Override
			public LedgerRepository answer(InvocationOnMock invocation) throws Throwable {
				if (isTestTurnOn.get()) {
					Thread.sleep(10000);
				}
				return (LedgerRepository) invocation.callRealMethod();
			}
		}).when(ledgerManagerMock0).getLedger(any());

		doAnswer(new Answer<LedgerRepository>() {
			@Override
			public LedgerRepository answer(InvocationOnMock invocation) throws Throwable {
				if (isTestTurnOn.get()) {
					Thread.sleep(10000);
				}
				return (LedgerRepository) invocation.callRealMethod();
			}
		}).when(ledgerManagerMock1).getLedger(any());

		testSDK(gateway, context);

		isTestTurnOn.set(false);
	}

	private static void testBlockHashTwoInconsistentSdk(GatewayTestRunner gateway, IntegratedContext context) {
		isTestTurnOn1.set(true);
		isTestTurnOn2.set(true);

		testSDK(gateway, context);

		isTestTurnOn1.set(false);
		isTestTurnOn2.set(false);

	}

	private static void testBlockHashFourInconsistentSdk(GatewayTestRunner gateway, IntegratedContext context) {

		isTestTurnOn0.set(true);
		isTestTurnOn1.set(true);
		isTestTurnOn2.set(true);
		isTestTurnOn3.set(true);

		testSDK(gateway, context);

		isTestTurnOn0.set(false);
		isTestTurnOn1.set(false);
		isTestTurnOn2.set(false);
		isTestTurnOn3.set(false);

	}

	private static void testStorageErrorBlockRollbackSdk(GatewayTestRunner gateway, IntegratedContext context) {

		((TestDbFactory) context.getNode(0).getStorageDB()).setErrorSetTurnOn(true);
		((TestDbFactory) context.getNode(1).getStorageDB()).setErrorSetTurnOn(true);
		((TestDbFactory) context.getNode(2).getStorageDB()).setErrorSetTurnOn(true);
		((TestDbFactory) context.getNode(3).getStorageDB()).setErrorSetTurnOn(true);

		testSDK(gateway, context);

		((TestDbFactory) context.getNode(0).getStorageDB()).setErrorSetTurnOn(false);
		((TestDbFactory) context.getNode(1).getStorageDB()).setErrorSetTurnOn(false);
		((TestDbFactory) context.getNode(2).getStorageDB()).setErrorSetTurnOn(false);
		((TestDbFactory) context.getNode(3).getStorageDB()).setErrorSetTurnOn(false);

	}

	private static void testTransactionRollbackSdk(GatewayTestRunner gateway, IntegratedContext context) {

		// 连接网关；
		GatewayServiceFactory gwsrvFact = GatewayServiceFactory.connect(gateway.getServiceAddress());
		BlockchainService bcsrv = gwsrvFact.getBlockchainService();

		HashDigest[] ledgerHashs = bcsrv.getLedgerHashs();

		AsymmetricKeypair adminKey = context.getNode(0).getPartiKeyPair();

		// 注册用户，并验证最终写入；
		BlockchainKeypair dataAccount = BlockchainKeyGenerator.getInstance().generate();

		// 定义交易；
		TransactionTemplate txTpl = bcsrv.newTransaction(ledgerHashs[0]);
		txTpl.dataAccounts().register(dataAccount.getIdentity());

		String dataKey = "jd_code";
		String dataVal = "www.jd.com";

		// Construct error kv version
		txTpl.dataAccount(dataAccount.getAddress()).setText(dataKey, dataVal, 1);

		PreparedTransaction prepTx = txTpl.prepare();

		prepTx.sign(adminKey);

		// 提交并等待共识返回；
		TransactionResponse txResp = prepTx.commit();

		// 验证结果;
		Node node0 = context.getNode(0);
		LedgerManage ledgerManager = new LedgerManager();

		KVStorageService storageService = node0.getStorageDB().connect(memDbConnString).getStorageService();

		LedgerQuery ledgerOfNode0 = ledgerManager.register(ledgerHashs[0], storageService, null, node0.getBindingConfig().getLedger(ledgerHashs[0]).getDataStructure());

	}

	private static void testInvalidUserSignerSdk(GatewayTestRunner gateway, IntegratedContext context) {
		// 连接网关；
		GatewayServiceFactory gwsrvFact = GatewayServiceFactory.connect(gateway.getServiceAddress());
		BlockchainService bcsrv = gwsrvFact.getBlockchainService();

		HashDigest[] ledgerHashs = bcsrv.getLedgerHashs();

		// Invalid signer
		PrivKey privKey = KeyGenUtils.decodePrivKeyWithRawPassword(PRIV_KEYS, PASSWORD);
		PubKey pubKey = KeyGenUtils.decodePubKey(PUB_KEYS);

		AsymmetricKeypair asymmetricKeypair = new AsymmetricKeypair(pubKey, privKey);

		// 注册用户，并验证最终写入；
		BlockchainKeypair user = BlockchainKeyGenerator.getInstance().generate();

		// 定义交易；
		TransactionTemplate txTpl = bcsrv.newTransaction(ledgerHashs[0]);
		txTpl.users().register(user.getIdentity());

		// 签名；
		PreparedTransaction ptx = txTpl.prepare();

		HashDigest transactionHash = ptx.getTransactionHash();

		ptx.sign(asymmetricKeypair);

		// 提交并等待共识返回；
		TransactionResponse txResp = ptx.commit();

		// 验证结果;
		Node node0 = context.getNode(0);
		LedgerManage ledgerManager = new LedgerManager();

		KVStorageService storageService = node0.getStorageDB().connect(memDbConnString).getStorageService();

		LedgerQuery ledgerOfNode0 = ledgerManager.register(ledgerHashs[0], storageService, null, node0.getBindingConfig().getLedger(ledgerHashs[0]).getDataStructure());

	}

	private static void testSDK(GatewayTestRunner gateway, IntegratedContext context) {
		// 连接网关；
		GatewayServiceFactory gwsrvFact = GatewayServiceFactory.connect(gateway.getServiceAddress());
		BlockchainService bcsrv = gwsrvFact.getBlockchainService();

		HashDigest[] ledgerHashs = bcsrv.getLedgerHashs();

		AsymmetricKeypair adminKey = context.getNode(0).getPartiKeyPair();

		BlockchainKeypair newUserAcount = testSDK_RegisterUser(adminKey, ledgerHashs[0], bcsrv, context);

		// BlockchainKeyPair newDataAccount = testSDK_RegisterDataAccount(adminKey,
		// ledgerHashs[0], bcsrv, context);
		//
		// testSDK_InsertData(adminKey, ledgerHashs[0], bcsrv,
		// newDataAccount.getAddress(), context);
		//
		// LedgerBlock latestBlock = testSDK_Contract(adminKey, ledgerHashs[0], bcsrv,
		// context);

	}

	private void testSDK_InsertData(AsymmetricKeypair adminKey, HashDigest ledgerHash,
			BlockchainService blockchainService, String dataAccountAddress, IntegratedContext context) {

		// 在本地定义注册账号的 TX；
		TransactionTemplate txTemp = blockchainService.newTransaction(ledgerHash);

		// --------------------------------------
		// 将商品信息写入到指定的账户中；
		// 对象将被序列化为 JSON 形式存储，并基于 JSON 结构建立查询索引；
		String dataAccount = dataAccountAddress;

		String dataKey = "jingdong" + new Random().nextInt(100000);
		String dataVal = "www.jd.com";

		txTemp.dataAccount(dataAccount).setText(dataKey, dataVal, -1);

		// TX 准备就绪；
		PreparedTransaction prepTx = txTemp.prepare();

		// 使用私钥进行签名；
		prepTx.sign(adminKey);

		// 提交交易；
		TransactionResponse txResp = prepTx.commit();

		Node node0 = context.getNode(0);
		LedgerQuery ledgerOfNode0 = node0.getLedgerManager().getLedger(ledgerHash);
		ledgerOfNode0.retrieveLatestBlock(); // 更新内存

		// 先验证应答
		TypedKVEntry[] kvDataEntries = blockchainService.getDataEntries(ledgerHash, dataAccountAddress, dataKey);
		for (TypedKVEntry kvDataEntry : kvDataEntries) {
			String valHexText = (String) kvDataEntry.getValue();
			byte[] valBytes = HexUtils.decode(valHexText);
			String valText = new String(valBytes);
			System.out.println(valText);
		}
	}

	private static BlockchainKeypair testSDK_RegisterUser(AsymmetricKeypair adminKey, HashDigest ledgerHash,
			BlockchainService blockchainService, IntegratedContext context) {
		// 注册用户，并验证最终写入；
		BlockchainKeypair user = BlockchainKeyGenerator.getInstance().generate();

		// 定义交易；
		TransactionTemplate txTpl = blockchainService.newTransaction(ledgerHash);
		txTpl.users().register(user.getIdentity());

		// 签名；
		PreparedTransaction ptx = txTpl.prepare();

		HashDigest transactionHash = ptx.getTransactionHash();

		ptx.sign(adminKey);

		// 提交并等待共识返回；
		TransactionResponse txResp = ptx.commit();

		// 验证结果;
		Node node0 = context.getNode(0);
		LedgerManage ledgerManager = new LedgerManager();

		KVStorageService storageService = node0.getStorageDB().connect(memDbConnString).getStorageService();

		LedgerQuery ledgerOfNode0 = ledgerManager.register(ledgerHash, storageService, null, node0.getBindingConfig().getLedger(ledgerHash).getDataStructure());

		return user;
	}

	private BlockchainKeypair testSDK_RegisterDataAccount(AsymmetricKeypair adminKey, HashDigest ledgerHash,
			BlockchainService blockchainService, IntegratedContext context) {
		// 注册数据账户，并验证最终写入；
		BlockchainKeypair dataAccount = BlockchainKeyGenerator.getInstance().generate();

		// 定义交易；
		TransactionTemplate txTpl = blockchainService.newTransaction(ledgerHash);
		txTpl.dataAccounts().register(dataAccount.getIdentity());

		// 签名；
		PreparedTransaction ptx = txTpl.prepare();

		HashDigest transactionHash = ptx.getTransactionHash();

		ptx.sign(adminKey);

		// 提交并等待共识返回；
		TransactionResponse txResp = ptx.commit();

		// 验证结果;
		Node node0 = context.getNode(0);
		// LedgerRepository ledgerOfNode0 =
		// node0.getLedgerManager().getLedger(ledgerHash);
		LedgerManage ledgerManager = new LedgerManager();

		KVStorageService storageService = node0.getStorageDB().connect(memDbConnString).getStorageService();

		LedgerQuery ledgerOfNode0 = ledgerManager.register(ledgerHash, storageService, null, node0.getBindingConfig().getLedger(ledgerHash).getDataStructure());
		long latestBlockHeight = ledgerOfNode0.retrieveLatestBlockHeight();

		return dataAccount;
	}

	private void testSDK_Query(AsymmetricKeypair adminKey, HashDigest ledgerHash, BlockchainService blockchainService,
			IntegratedContext context, BlockchainKeypair newUserAcount, BlockchainKeypair newDataAcount) {

		Bytes userAddress = newUserAcount.getAddress();
		Bytes dataAddress = newDataAcount.getAddress();

		Node node0 = context.getNode(0);
		// LedgerRepository ledgerOfNode0 =
		// node0.getLedgerManager().getLedger(ledgerHash);
		LedgerManage ledgerManager = new LedgerManager();

		KVStorageService storageService = node0.getStorageDB().connect(memDbConnString).getStorageService();

		LedgerQuery ledgerOfNode0 = ledgerManager.register(ledgerHash, storageService, null, node0.getBindingConfig().getLedger(ledgerHash).getDataStructure());

		// getLedgerHashs
		HashDigest[] ledgerHashs = blockchainService.getLedgerHashs();
		for (HashDigest hashDigest : ledgerHashs) {
			if (hashDigest.equals(ledgerHash)) {
				break;
			}
			System.out.println("Query getLedgerHashs error! ledgerHash not exist!");
		}

		// getLedger
		LedgerInfo ledgerInfo = blockchainService.getLedger(ledgerHash);
		long ledgerHeight = ledgerInfo.getLatestBlockHeight();

		// getConsensusParticipants
		ParticipantNode[] consensusParticipants = blockchainService.getConsensusParticipants(ledgerHash);
		for (int i = 0; i < 4; i++) {
		}

		// getBlock
		for (int i = 0; i < ledgerHeight + 1; i++) {
			LedgerBlock expectBlock = ledgerOfNode0.getBlock(i);
		}

		// getTransactionCount according to blockhash
		for (int i = 0; i < ledgerHeight + 1; i++) {
			LedgerBlock expectBlock = ledgerOfNode0.getBlock(i);
			long expectTransactionCount = ledgerOfNode0.getTransactionSet(expectBlock).getTotalCount();
			long actualTransactionCount = blockchainService.getTransactionCount(ledgerHash, expectBlock.getHash());
		}

		// getDataAccountCount according to blockhash
		for (int i = 0; i < ledgerHeight + 1; i++) {
			LedgerBlock expectBlock = ledgerOfNode0.getBlock(i);
			long expectDataCount = ledgerOfNode0.getDataAccountSet(expectBlock).getTotal();
			long actualDataCount = blockchainService.getDataAccountCount(ledgerHash, expectBlock.getHash());
		}

		// getUserCount according to blockhash
		for (int i = 0; i < ledgerHeight + 1; i++) {
			LedgerBlock expectBlock = ledgerOfNode0.getBlock(i);
			long expectUserCount = ledgerOfNode0.getUserAccountSet(expectBlock).getTotal();
			long actualUserCount = blockchainService.getUserCount(ledgerHash, expectBlock.getHash());
		}

		// getContractCount according to blockhash
		for (int i = 0; i < ledgerHeight + 1; i++) {
			LedgerBlock expectBlock = ledgerOfNode0.getBlock(i);
			long expectContractCount = ledgerOfNode0.getContractAccountSet(expectBlock).getTotal();
			long actualContractCount = blockchainService.getContractCount(ledgerHash, expectBlock.getHash());
		}

		// getTransactionCount according to height
		// getDataAccountCount according to height
		// getUserCount according to height
		// getContractCount according to height
		long expectTransactionTotal = 0;
		long expectUserTotal = 0;
		long expectDataTotal = 0;
		long expectContractTotal = 0;
		for (int i = 0; i < ledgerHeight + 1; i++) {
			// actual block acount total
			long transactionCount = blockchainService.getTransactionCount(ledgerHash, i);
			long userCount = blockchainService.getUserCount(ledgerHash, i);
			long dataCount = blockchainService.getDataAccountCount(ledgerHash, i);
			long contractCount = blockchainService.getContractCount(ledgerHash, i);

			// expect block acount total
			LedgerBlock ledgerBlock = ledgerOfNode0.getBlock(i);
			expectTransactionTotal = ledgerOfNode0.getTransactionSet(ledgerBlock).getTotalCount();
			expectUserTotal = ledgerOfNode0.getUserAccountSet(ledgerBlock).getTotal();
			expectDataTotal = ledgerOfNode0.getDataAccountSet(ledgerBlock).getTotal();
			expectContractTotal = ledgerOfNode0.getContractAccountSet(ledgerBlock).getTotal();
		}

		// getTransactionTotalCount
		long actualTransactionTotal = blockchainService.getTransactionTotalCount(ledgerHash);

		// getUserTotalCount
		long actualUserTotal = blockchainService.getUserTotalCount(ledgerHash);

		// getDataAccountTotalCount
		long actualDataAccountTotal = blockchainService.getDataAccountTotalCount(ledgerHash);

		// getContractTotalCount
		long actualContractAccountTotal = blockchainService.getContractTotalCount(ledgerHash);

		// getTransactions
		// getTransactionByContentHash
		// getTransactionStateByContentHash
		// ledger-core not implement
		// for (int i = 0; i < ledgerHeight + 1; i++) {
		// LedgerBlock ledgerBlock = ledgerOfNode0.getBlock(i);
		// HashDigest blockHash = ledgerBlock.getHash();
		// long expectCount =
		// ledgerOfNode0.getTransactionSet(ledgerBlock).getTotalCount();
		// long actualCount = blockchainService.getTransactionCount(ledgerHash, i);
		// LedgerTransaction[] ledgerTransactions1 =
		// blockchainService.getTransactions(ledgerHash, i, 0, (int)(actualCount - 1));
		// LedgerTransaction[] ledgerTransactions2 =
		// blockchainService.getTransactions(ledgerHash, blockHash, 0, (int)(expectCount
		// - 1));
		// assertEquals(ledgerTransactions1.length, ledgerTransactions2.length);
		// assertArrayEquals(ledgerTransactions1, ledgerTransactions2);
		//
		// for (LedgerTransaction ledgerTransaction : ledgerTransactions1) {
		// assertEquals(ledgerTransaction,
		// blockchainService.getTransactionByContentHash(ledgerHash,
		// ledgerTransaction.getTransactionContent().getHash()));
		// assertEquals(TransactionState.SUCCESS,
		// blockchainService.getTransactionStateByContentHash(ledgerHash,
		// ledgerTransaction.getTransactionContent().getHash()));
		// }
		// }
		// getUser
		UserInfo userInfo = blockchainService.getUser(ledgerHash, userAddress.toString());

		// getDataAccount
		BlockchainIdentity accountHeader = blockchainService.getDataAccount(ledgerHash, dataAddress.toString());

		// getDataEntries

		return;
	}

	public static ConsensusProvider getConsensusProvider(String providerName) {
		return ConsensusProviders.getProvider(providerName);
	}

	private static IntegratedContext initLedgers() {
		Prompter consolePrompter = new PresetAnswerPrompter("N"); // new ConsolePrompter();
		LedgerInitProperties initSetting = loadInitSetting_integration();
		Properties props = LedgerInitializeWebTest
				.loadConsensusSetting(LedgerInitConsensusConfig.bftsmartConfig.getConfigPath());
		ConsensusProvider csProvider = getConsensusProvider(LedgerInitConsensusConfig.bftsmartConfig.getProvider());
		ConsensusViewSettings csProps = csProvider.getSettingsFactory().getConsensusSettingsBuilder()
				.createSettings(props, Utils.loadParticipantNodes());

		// 启动服务器；
		NetworkAddress initAddr0 = initSetting.getConsensusParticipant(0).getInitializerAddress();
		LedgerInitializeWebTest.NodeWebContext nodeCtx0 = new LedgerInitializeWebTest.NodeWebContext(0, initAddr0);

		NetworkAddress initAddr1 = initSetting.getConsensusParticipant(1).getInitializerAddress();
		LedgerInitializeWebTest.NodeWebContext nodeCtx1 = new LedgerInitializeWebTest.NodeWebContext(1, initAddr1);

		NetworkAddress initAddr2 = initSetting.getConsensusParticipant(2).getInitializerAddress();
		LedgerInitializeWebTest.NodeWebContext nodeCtx2 = new LedgerInitializeWebTest.NodeWebContext(2, initAddr2);

		NetworkAddress initAddr3 = initSetting.getConsensusParticipant(3).getInitializerAddress();
		LedgerInitializeWebTest.NodeWebContext nodeCtx3 = new LedgerInitializeWebTest.NodeWebContext(3, initAddr3);
		PrivKey privkey0 = KeyGenUtils.decodePrivKeyWithRawPassword(LedgerInitializeWebTest.PRIV_KEYS[0],
				LedgerInitializeWebTest.PASSWORD);
		PrivKey privkey1 = KeyGenUtils.decodePrivKeyWithRawPassword(LedgerInitializeWebTest.PRIV_KEYS[1],
				LedgerInitializeWebTest.PASSWORD);
		PrivKey privkey2 = KeyGenUtils.decodePrivKeyWithRawPassword(LedgerInitializeWebTest.PRIV_KEYS[2],
				LedgerInitializeWebTest.PASSWORD);
		PrivKey privkey3 = KeyGenUtils.decodePrivKeyWithRawPassword(LedgerInitializeWebTest.PRIV_KEYS[3],
				LedgerInitializeWebTest.PASSWORD);

		String encodedPassword = KeyGenUtils.encodePasswordAsBase58(LedgerInitializeWebTest.PASSWORD);

		CountDownLatch quitLatch = new CountDownLatch(4);

		TestDbFactory dbFactory0 = new TestDbFactory(new CompositeConnectionFactory());
		dbFactory0.setErrorSetTurnOn(false);
		DBConnectionConfig testDb0 = new DBConnectionConfig();
		testDb0.setConnectionUri("memory://local/0");
		LedgerBindingConfig bindingConfig0 = new LedgerBindingConfig();
		AsyncCallback<HashDigest> callback0 = nodeCtx0.startInitCommand(privkey0, encodedPassword, initSetting, csProps,
				csProvider, testDb0, consolePrompter, bindingConfig0, quitLatch, dbFactory0);

		TestDbFactory dbFactory1 = new TestDbFactory(new CompositeConnectionFactory());
		dbFactory1.setErrorSetTurnOn(false);
		DBConnectionConfig testDb1 = new DBConnectionConfig();
		testDb1.setConnectionUri("memory://local/1");
		LedgerBindingConfig bindingConfig1 = new LedgerBindingConfig();
		AsyncCallback<HashDigest> callback1 = nodeCtx1.startInitCommand(privkey1, encodedPassword, initSetting, csProps,
				csProvider, testDb1, consolePrompter, bindingConfig1, quitLatch, dbFactory1);

		TestDbFactory dbFactory2 = new TestDbFactory(new CompositeConnectionFactory());
		dbFactory2.setErrorSetTurnOn(false);
		DBConnectionConfig testDb2 = new DBConnectionConfig();
		testDb2.setConnectionUri("memory://local/2");
		LedgerBindingConfig bindingConfig2 = new LedgerBindingConfig();
		AsyncCallback<HashDigest> callback2 = nodeCtx2.startInitCommand(privkey2, encodedPassword, initSetting, csProps,
				csProvider, testDb2, consolePrompter, bindingConfig2, quitLatch, dbFactory2);

		TestDbFactory dbFactory3 = new TestDbFactory(new CompositeConnectionFactory());
		dbFactory3.setErrorSetTurnOn(false);
		DBConnectionConfig testDb3 = new DBConnectionConfig();
		testDb3.setConnectionUri("memory://local/3");
		LedgerBindingConfig bindingConfig3 = new LedgerBindingConfig();
		AsyncCallback<HashDigest> callback3 = nodeCtx3.startInitCommand(privkey3, encodedPassword, initSetting, csProps,
				csProvider, testDb3, consolePrompter, bindingConfig3, quitLatch, dbFactory3);

		HashDigest ledgerHash0 = callback0.waitReturn();
		HashDigest ledgerHash1 = callback1.waitReturn();
		HashDigest ledgerHash2 = callback2.waitReturn();
		HashDigest ledgerHash3 = callback3.waitReturn();

		LedgerQuery ledger0 = nodeCtx0.registerLedger(ledgerHash0, initSetting.getLedgerDataStructure());
		LedgerQuery ledger1 = nodeCtx1.registerLedger(ledgerHash1, initSetting.getLedgerDataStructure());
		LedgerQuery ledger2 = nodeCtx2.registerLedger(ledgerHash2, initSetting.getLedgerDataStructure());
		LedgerQuery ledger3 = nodeCtx3.registerLedger(ledgerHash3, initSetting.getLedgerDataStructure());

		IntegratedContext context = new IntegratedContext();

		context.setLedgerHash(ledgerHash0);
		context.setCryptoSetting(ledger0.getAdminInfo().getSettings().getCryptoSetting());

		Node node0 = new Node(0);
		node0.setConsensusSettings(csProps);
		node0.setLedgerManager(nodeCtx0.getLedgerManager());
		node0.setStorageDB(nodeCtx0.getStorageDB());
		node0.setPartiKeyPair(new AsymmetricKeypair(initSetting.getConsensusParticipant(0).getPubKey(), privkey0));
		node0.setBindingConfig(bindingConfig0);
		node0.setConnectionConfig(testDb0);
		context.addNode(node0);

		Node node1 = new Node(1);
		node1.setConsensusSettings(csProps);
		node1.setLedgerManager(nodeCtx1.getLedgerManager());
		node1.setStorageDB(nodeCtx1.getStorageDB());
		node1.setPartiKeyPair(new AsymmetricKeypair(initSetting.getConsensusParticipant(1).getPubKey(), privkey1));
		node1.setBindingConfig(bindingConfig1);
		node1.setConnectionConfig(testDb1);
		context.addNode(node1);

		Node node2 = new Node(2);
		node2.setConsensusSettings(csProps);
		node2.setLedgerManager(nodeCtx2.getLedgerManager());
		node2.setStorageDB(nodeCtx2.getStorageDB());
		node2.setPartiKeyPair(new AsymmetricKeypair(initSetting.getConsensusParticipant(2).getPubKey(), privkey2));
		node2.setBindingConfig(bindingConfig2);
		node2.setConnectionConfig(testDb2);
		context.addNode(node2);

		Node node3 = new Node(3);
		node3.setConsensusSettings(csProps);
		node3.setLedgerManager(nodeCtx3.getLedgerManager());
		node3.setStorageDB(nodeCtx3.getStorageDB());
		node3.setPartiKeyPair(new AsymmetricKeypair(initSetting.getConsensusParticipant(3).getPubKey(), privkey3));
		node3.setBindingConfig(bindingConfig3);
		node3.setConnectionConfig(testDb3);
		context.addNode(node3);

		nodeCtx0.closeServer();
		nodeCtx1.closeServer();
		nodeCtx2.closeServer();
		nodeCtx3.closeServer();

		return context;
	}

	public static LedgerInitProperties loadInitSetting_integration() {
		ClassPathResource ledgerInitSettingResource = new ClassPathResource("ledger_init_test_web2.init");
		try (InputStream in = ledgerInitSettingResource.getInputStream()) {
			LedgerInitProperties setting = LedgerInitProperties.resolve(in);
			return setting;
		} catch (IOException e) {
			throw new IllegalStateException(e.getMessage(), e);
		}
	}

	private LedgerBlock testSDK_Contract(AsymmetricKeypair adminKey, HashDigest ledgerHash,
			BlockchainService blockchainService, IntegratedContext context) {
		// valid the basic data in contract;
		prepareContractData(adminKey, ledgerHash, blockchainService, context);

		BlockchainKeypair userKey = BlockchainKeyGenerator.getInstance().generate();

		// 定义交易；
		TransactionTemplate txTpl = blockchainService.newTransaction(ledgerHash);
		byte[] contractCode = getChainCodeBytes();

		txTpl.users().register(userKey.getIdentity());

		txTpl.contracts().deploy(contractDeployKey.getIdentity(), contractCode);

		// 签名；
		PreparedTransaction ptx = txTpl.prepare();
		ptx.sign(adminKey);

		// 提交并等待共识返回；
		TransactionResponse txResp = ptx.commit();

		// 验证结果；
		txResp.getContentHash();

		Node node0 = context.getNode(0);
		LedgerQuery ledgerOfNode0 = node0.getLedgerManager().getLedger(ledgerHash);
		LedgerBlock block = ledgerOfNode0.getBlock(txResp.getBlockHeight());
		byte[] contractCodeInDb = ledgerOfNode0.getContractAccountSet(block).getAccount(contractDeployKey.getAddress())
				.getChainCode();
		txContentHash = ptx.getTransactionHash();

		// execute the contract;
		testContractExe(adminKey, ledgerHash, userKey, blockchainService, context);

		return block;
	}

	private void testContractExe(AsymmetricKeypair adminKey, HashDigest ledgerHash, BlockchainKeypair userKey,
			BlockchainService blockchainService, IntegratedContext context) {
		LedgerInfo ledgerInfo = blockchainService.getLedger(ledgerHash);
		LedgerBlock previousBlock = blockchainService.getBlock(ledgerHash, ledgerInfo.getLatestBlockHeight() - 1);

		// 定义交易；
		TransactionTemplate txTpl = blockchainService.newTransaction(ledgerHash);

//		txTpl.contractEvents().send(contractDeployKey.getAddress(), eventName,
//				("888##abc##" + contractDataKey.getAddress() + "##" + previousBlock.getHash().toBase58() + "##"
//						+ userKey.getAddress() + "##" + contractDeployKey.getAddress() + "##" + txContentHash.toBase58()
//						+ "##SOME-VALUE").getBytes());

		// 签名；
		PreparedTransaction ptx = txTpl.prepare();
		ptx.sign(adminKey);

		// 提交并等待共识返回；
		TransactionResponse txResp = ptx.commit();

		// 验证结果；
		txResp.getContentHash();

		LedgerInfo latestLedgerInfo = blockchainService.getLedger(ledgerHash);

		Node node0 = context.getNode(0);
		LedgerQuery ledgerOfNode0 = node0.getLedgerManager().getLedger(ledgerHash);
		LedgerBlock backgroundLedgerBlock = ledgerOfNode0.retrieveLatestBlock();

		// 验证合约中的赋值，外部可以获得;
		DataAccountSet dataAccountSet = ledgerOfNode0.getDataAccountSet(backgroundLedgerBlock);
		AsymmetricKeypair key = Crypto.getSignatureFunction("ED25519").generateKeypair();
		PubKey pubKey = key.getPubKey();
		Bytes dataAddress = AddressEncoding.generateAddress(pubKey);

		// 验证userAccount，从合约内部赋值，然后外部验证;由于目前不允许输入重复的key，所以在内部合约中构建的key，不便于在外展示，屏蔽之;
		// UserAccountSet userAccountSet =
		// ledgerOfNode0.getUserAccountSet(backgroundLedgerBlock);
		// PubKey userPubKey = new PubKey(CryptoAlgorithm.ED25519,
		// userPubKeyVal.getBytes());
		// String userAddress = AddressEncoding.generateAddress(userPubKey);
		// assertEquals(userAddress, userAccountSet.getUser(userAddress).getAddress());
	}

	private void prepareContractData(AsymmetricKeypair adminKey, HashDigest ledgerHash,
			BlockchainService blockchainService, IntegratedContext context) {

		// 定义交易；
		TransactionTemplate txTpl = blockchainService.newTransaction(ledgerHash);
		// 注册数据账户，并验证最终写入；
		txTpl.dataAccounts().register(contractDataKey.getIdentity());
		DataAccountKVSetOperation kvsetOP = txTpl.dataAccount(contractDataKey.getAddress())
				.setText("A", "Value_A_0", -1).setText("B", "Value_B_0", -1)
				.setText(KEY_TOTAL, "total value,dataAccount", -1).setText(KEY_ABC, "abc value,dataAccount", -1)
				// 所有的模拟数据都在这个dataAccount中填充;
				.setBytes("ledgerHash", ledgerHash.getRawDigest(), -1).getOperation();

		// 签名；
		PreparedTransaction ptx = txTpl.prepare();
		ptx.sign(adminKey);

		// 提交并等待共识返回；
		TransactionResponse txResp = ptx.commit();

		// 验证结果；
		Node node0 = context.getNode(0);

		LedgerQuery ledgerOfNode0 = node0.getLedgerManager().getLedger(ledgerHash);
		LedgerBlock block = ledgerOfNode0.getBlock(txResp.getBlockHeight());
		BytesValue val1InDb = ledgerOfNode0.getDataAccountSet(block).getAccount(contractDataKey.getAddress())
				.getDataset().getValue("A");
		BytesValue val2InDb = ledgerOfNode0.getDataAccountSet(block).getAccount(contractDataKey.getAddress())
				.getDataset().getValue(KEY_TOTAL);
	}

	/**
	 * 根据合约构建字节数组;
	 * 
	 * @return
	 */
	private byte[] getChainCodeBytes() {
		// 构建合约的字节数组;
		byte[] contractCode = null;
		File file = null;
		InputStream input = null;
		try {
			ClassPathResource contractPath = new ClassPathResource(contractZipName);
			file = new File(contractPath.getURI());
			input = new FileInputStream(file);
			// 这种暴力的读取压缩包，在class解析时有问题，所有需要改进;
			contractCode = new byte[input.available()];
			input.read(contractCode);
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				if (input != null) {
					input.close();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return contractCode;
	}
}
