package test.com.jd.blockchain.intgr;

import static test.com.jd.blockchain.intgr.IntegrationBase.buildLedgers;
import static test.com.jd.blockchain.intgr.IntegrationBase.peerNodeStart;
import static test.com.jd.blockchain.intgr.IntegrationBase.validKeyPair;
import static test.com.jd.blockchain.intgr.IntegrationBase.validKvWrite;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.junit.Test;

import com.jd.blockchain.consensus.ConsensusProviders;
import com.jd.blockchain.consensus.bftsmart.BftsmartConsensusViewSettings;
import com.jd.blockchain.crypto.AsymmetricKeypair;
import com.jd.blockchain.crypto.HashDigest;
import com.jd.blockchain.crypto.KeyGenUtils;
import com.jd.blockchain.crypto.PrivKey;
import com.jd.blockchain.crypto.PubKey;
import com.jd.blockchain.gateway.GatewayConfigProperties;
import com.jd.blockchain.ledger.BlockchainKeypair;
import com.jd.blockchain.ledger.ConsensusSettingsUpdateOperation;
import com.jd.blockchain.ledger.ContractCodeDeployOperation;
import com.jd.blockchain.ledger.ContractEventSendOperation;
import com.jd.blockchain.ledger.DataAccountKVSetOperation;
import com.jd.blockchain.ledger.DataAccountRegisterOperation;
import com.jd.blockchain.ledger.EventAccountRegisterOperation;
import com.jd.blockchain.ledger.EventPublishOperation;
import com.jd.blockchain.ledger.LedgerInitOperation;
import com.jd.blockchain.ledger.LedgerTransaction;
import com.jd.blockchain.ledger.Operation;
import com.jd.blockchain.ledger.ParticipantRegisterOperation;
import com.jd.blockchain.ledger.ParticipantStateUpdateOperation;
import com.jd.blockchain.ledger.RolesConfigureOperation;
import com.jd.blockchain.ledger.UserAuthorizeOperation;
import com.jd.blockchain.ledger.UserInfoSetOperation;
import com.jd.blockchain.ledger.UserRegisterOperation;
import com.jd.blockchain.ledger.core.LedgerQuery;
import com.jd.blockchain.sdk.BlockchainService;
import com.jd.blockchain.sdk.client.GatewayServiceFactory;
import com.jd.blockchain.storage.service.DbConnectionFactory;
import com.jd.blockchain.test.PeerServer;
import com.jd.blockchain.tools.initializer.LedgerBindingConfig;

import test.com.jd.blockchain.intgr.initializer.LedgerInitializeTest;
import test.com.jd.blockchain.intgr.initializer.LedgerInitializeWeb4Nodes;
import utils.Property;
import utils.concurrent.ThreadInvoker;

public class IntegrationTest4Bftsmart {

	private static final boolean isRegisterUser = true;

	private static final boolean isRegisterDataAccount = true;

	private static final boolean isRegisterParticipant = true;

	private static final boolean isParticipantStateUpdate = true;

	private static final boolean isConsensusSettingUpdate = false;

	private static final boolean isWriteKv = true;

	private static final String DB_TYPE_MEM = "mem";

	private static final String DB_TYPE_REDIS = "redis";

	public static final String DB_TYPE_ROCKSDB = "rocksdb";

	public static final String BFTSMART_PROVIDER = "com.jd.blockchain.consensus.bftsmart.BftsmartConsensusProvider";

	@Test
	public void test4Memory() {
		Configurator.setLevel("bftsmart", Level.DEBUG);
		Configurator.setLevel("com.jd.blockchain", Level.DEBUG);
		test(LedgerInitConsensusConfig.bftsmartProvider, DB_TYPE_MEM, LedgerInitConsensusConfig.memConnectionStrings);
	}

	@Test
	public void test4Redis() {
//        test(LedgerInitConsensusConfig.bftsmartProvider, DB_TYPE_REDIS, LedgerInitConsensusConfig.redisConnectionStrings);
	}

	public void test(String[] providers, String dbType, String[] dbConnections) {

		final ExecutorService sendReqExecutors = Executors.newFixedThreadPool(20);

		// 内存账本初始化
		HashDigest ledgerHash = initLedger(dbConnections);

		// 启动Peer节点
		PeerServer[] peerNodes = peerNodeStart(ledgerHash, dbType);

		try {
			// 休眠20秒，保证Peer节点启动成功
			Thread.sleep(20000);
		} catch (Exception e) {
			e.printStackTrace();
		}

		DbConnectionFactory dbConnectionFactory0 = peerNodes[0].getDBConnectionFactory();
		DbConnectionFactory dbConnectionFactory1 = peerNodes[1].getDBConnectionFactory();
		DbConnectionFactory dbConnectionFactory2 = peerNodes[2].getDBConnectionFactory();
		DbConnectionFactory dbConnectionFactory3 = peerNodes[3].getDBConnectionFactory();

		String encodedBase58Pwd = KeyGenUtils.encodePasswordAsBase58(LedgerInitializeTest.PASSWORD);

		GatewayConfigProperties.KeyPairConfig gwkey0 = new GatewayConfigProperties.KeyPairConfig();
		gwkey0.setPubKeyValue(IntegrationBase.PUB_KEYS[0]);
		gwkey0.setPrivKeyValue(IntegrationBase.PRIV_KEYS[0]);
		gwkey0.setPrivKeyPassword(encodedBase58Pwd);

		GatewayTestRunner gateway = new GatewayTestRunner("127.0.0.1", 11000, gwkey0, providers, null,
				peerNodes[0].getServiceAddress());

		ThreadInvoker.AsyncCallback<Object> gwStarting = gateway.start();

		gwStarting.waitReturn();

		// 执行测试用例之前，校验每个节点的一致性；
		LedgerQuery[] ledgers = buildLedgers(
				new LedgerBindingConfig[] { peerNodes[0].getLedgerBindingConfig(),
						peerNodes[1].getLedgerBindingConfig(), peerNodes[2].getLedgerBindingConfig(),
						peerNodes[3].getLedgerBindingConfig(), },
				new DbConnectionFactory[] { dbConnectionFactory0, dbConnectionFactory1, dbConnectionFactory2,
						dbConnectionFactory3 });

		IntegrationBase.testConsistencyAmongNodes(ledgers);

		LedgerQuery ledgerRepository = ledgers[0];

		try {
			// 休眠20秒，保证Peer节点启动成功
			Thread.sleep(20000);
		} catch (Exception e) {
			e.printStackTrace();
		}

		GatewayServiceFactory gwsrvFact = GatewayServiceFactory.connect(gateway.getServiceAddress());

		PrivKey privkey0 = KeyGenUtils.decodePrivKeyWithRawPassword(IntegrationBase.PRIV_KEYS[0],
				IntegrationBase.PASSWORD);

		PubKey pubKey0 = KeyGenUtils.decodePubKey(IntegrationBase.PUB_KEYS[0]);

		AsymmetricKeypair adminKey = new AsymmetricKeypair(pubKey0, privkey0);

		BlockchainService blockchainService = gwsrvFact.getBlockchainService();

		int size = 15;
		CountDownLatch countDownLatch = new CountDownLatch(size);
		if (isRegisterUser) {
			for (int i = 0; i < size; i++) {
				sendReqExecutors.execute(() -> {

					System.out.printf(" sdk execute time = %s threadId = %s \r\n", System.currentTimeMillis(),
							Thread.currentThread().getId());
					IntegrationBase.KeyPairResponse userResponse = IntegrationBase.testSDK_RegisterUser(adminKey,
							ledgerHash, blockchainService);

//                    validKeyPair(userResponse, ledgerRepository, IntegrationBase.KeyPairType.USER);
					countDownLatch.countDown();
				});
			}
		}
		try {
			countDownLatch.await();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		if (isRegisterDataAccount) {
			IntegrationBase.KeyPairResponse dataAccountResponse = IntegrationBase.testSDK_RegisterDataAccount(adminKey,
					ledgerHash, blockchainService);

			validKeyPair(dataAccountResponse, ledgerRepository, IntegrationBase.KeyPairType.DATAACCOUNT);

			if (isWriteKv) {

				for (int m = 0; m < 13; m++) {
					BlockchainKeypair da = dataAccountResponse.keyPair;
					IntegrationBase.KvResponse kvResponse = IntegrationBase.testSDK_InsertData(adminKey, ledgerHash,
							blockchainService, da.getAddress());
					validKvWrite(kvResponse, ledgerRepository, blockchainService);
				}
			}
		}

		long participantCount = ledgerRepository.getAdminInfo(ledgerRepository.retrieveLatestBlock())
				.getParticipantCount();

		long userCount = ledgerRepository.getUserAccountSet(ledgerRepository.retrieveLatestBlock()).getTotal();

		System.out.printf("before add participant: participantCount = %d, userCount = %d\r\n", (int) participantCount,
				(int) userCount);

		IntegrationBase.KeyPairResponse participantResponse;
		if (isRegisterParticipant) {
			participantResponse = IntegrationBase.testSDK_RegisterParticipant(adminKey, ledgerHash, blockchainService);
		}

		participantCount = ledgerRepository.getAdminInfo(ledgerRepository.retrieveLatestBlock()).getParticipantCount();

		userCount = ledgerRepository.getUserAccountSet(ledgerRepository.retrieveLatestBlock()).getTotal();

		System.out.printf("after add participant: participantCount = %d, userCount = %d\r\n", (int) participantCount,
				(int) userCount);

		BftsmartConsensusViewSettings consensusSettings = (BftsmartConsensusViewSettings) ConsensusProviders
				.getProvider(BFTSMART_PROVIDER).getSettingsFactory().getConsensusSettingsEncoder()
				.decode(ledgerRepository.getAdminInfo().getSettings().getConsensusSetting().toBytes());
		System.out.printf("update participant state before ,old consensus env node num = %d\r\n",
				consensusSettings.getNodes().length);

		for (int i = 0; i < participantCount; i++) {
			System.out.printf("part%d state = %d\r\n", i,
					ledgerRepository.getAdminInfo(ledgerRepository.retrieveLatestBlock()).getParticipants()[i]
							.getParticipantNodeState().CODE);
		}

		if (isParticipantStateUpdate) {
			IntegrationBase.testSDK_UpdateParticipantState(adminKey,
					new BlockchainKeypair(participantResponse.getKeyPair().getPubKey(),
							participantResponse.getKeyPair().getPrivKey()),
					ledgerHash, blockchainService);
		}

		BftsmartConsensusViewSettings consensusSettingsNew = (BftsmartConsensusViewSettings) ConsensusProviders
				.getProvider(BFTSMART_PROVIDER).getSettingsFactory().getConsensusSettingsEncoder()
				.decode(ledgerRepository.getAdminInfo(ledgerRepository.retrieveLatestBlock()).getSettings()
						.getConsensusSetting().toBytes());

		System.out.printf("update participant state after ,new consensus env node num = %d\r\n",
				consensusSettingsNew.getNodes().length);
		for (int i = 0; i < participantCount; i++) {
			System.out.printf("part%d state = %d\r\n", i,
					ledgerRepository.getAdminInfo(ledgerRepository.retrieveLatestBlock()).getParticipants()[i]
							.getParticipantNodeState().CODE);
		}

		if (isConsensusSettingUpdate) {
			BftsmartConsensusViewSettings consensusSettings2 = (BftsmartConsensusViewSettings) ConsensusProviders
					.getProvider(providers[0]).getSettingsFactory().getConsensusSettingsEncoder()
					.decode(ledgerRepository.getAdminInfo().getSettings().getConsensusSetting().toBytes());
			for (Property property : consensusSettings2.getSystemConfigs()) {
				System.out.printf("before update name  = %s, before update value  = %s\r\n", property.getName(),
						property.getValue());
			}

			IntegrationBase.testSDK_Update_Consensus_Settings(adminKey, ledgerHash, blockchainService);

			BftsmartConsensusViewSettings consensusSettings3 = (BftsmartConsensusViewSettings) ConsensusProviders
					.getProvider(providers[0]).getSettingsFactory().getConsensusSettingsEncoder()
					.decode(ledgerRepository.getAdminInfo(ledgerRepository.retrieveLatestBlock()).getSettings()
							.getConsensusSetting().toBytes());
			for (Property property : consensusSettings3.getSystemConfigs()) {
				System.out.printf("after update name  = %s, after update value  = %s\r\n", property.getName(),
						property.getValue());
			}
		}

		System.out.println("----------------- test tx querying and operation resolving -----------------");
		LedgerTransaction[] txs = gwsrvFact.getBlockchainService().getTransactions(ledgerHash, ledgerRepository.retrieveLatestBlockHeight(), 0, 100);
		for (int i = 0; i < txs.length; i++) {
			System.out.println("---- tx[" + i + "]: operations ----");
			Operation[] ops = txs[i].getRequest().getTransactionContent().getOperations();
			for (int j = 0; j < ops.length; j++) {
				Class<?> opType = resolveOperationType(ops[j]);
				
				System.out.println(String.format("op[%s]: [OP_TYPE=%s] [ReallyType=%s]", j, opType == null ? "UnknowOperation" : opType.getName() , ops[j].getClass().getName()));
			}
		}

		try {
			System.out.println("----------------- Init Completed -----------------");
			Thread.sleep(Integer.MAX_VALUE);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		IntegrationBase.testConsistencyAmongNodes(ledgers);
	}

	private Class<?> resolveOperationType(Operation op) {
		if (op instanceof ConsensusSettingsUpdateOperation) {
			return ConsensusSettingsUpdateOperation.class;
		} else if (op instanceof ContractCodeDeployOperation) {
			return ContractCodeDeployOperation.class;
		} else if (op instanceof ContractEventSendOperation) {
			return ContractEventSendOperation.class;
		} else if (op instanceof DataAccountKVSetOperation) {
			return DataAccountKVSetOperation.class;
		} else if (op instanceof DataAccountRegisterOperation) {
			return DataAccountRegisterOperation.class;
		} else if (op instanceof EventAccountRegisterOperation) {
			return EventAccountRegisterOperation.class;
		} else if (op instanceof EventPublishOperation) {
			return EventPublishOperation.class;
		} else if (op instanceof LedgerInitOperation) {
			return LedgerInitOperation.class;
		} else if (op instanceof ParticipantRegisterOperation) {
			return ParticipantRegisterOperation.class;
		} else if (op instanceof ParticipantStateUpdateOperation) {
			return ParticipantStateUpdateOperation.class;
		} else if (op instanceof RolesConfigureOperation) {
			return RolesConfigureOperation.class;
		} else if (op instanceof UserAuthorizeOperation) {
			return UserAuthorizeOperation.class;
		} else if (op instanceof UserInfoSetOperation) {
			return UserInfoSetOperation.class;
		} else if (op instanceof UserRegisterOperation) {
			return UserRegisterOperation.class;
		}
		return null;
	}

	private HashDigest initLedger(String[] dbConnections) {
		LedgerInitializeWeb4Nodes ledgerInit = new LedgerInitializeWeb4Nodes();
		HashDigest ledgerHash = ledgerInit.testInitWith4Nodes(LedgerInitConsensusConfig.bftsmartConfig, dbConnections);
		System.out.printf("LedgerHash = %s \r\n", ledgerHash.toBase58());
		return ledgerHash;
	}
}
