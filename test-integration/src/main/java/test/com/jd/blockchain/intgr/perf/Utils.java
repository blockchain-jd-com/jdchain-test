package test.com.jd.blockchain.intgr.perf;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import com.jd.blockchain.ledger.LedgerDataStructure;
import org.bouncycastle.util.Arrays;
import org.springframework.core.io.ClassPathResource;

import com.jd.binaryproto.BinaryProtocol;
import com.jd.blockchain.consensus.ConsensusProvider;
import com.jd.blockchain.consensus.ConsensusViewSettings;
import com.jd.blockchain.consensus.Replica;
import com.jd.blockchain.crypto.AddressEncoding;
import com.jd.blockchain.crypto.AsymmetricKeypair;
import com.jd.blockchain.crypto.Crypto;
import com.jd.blockchain.crypto.CryptoAlgorithm;
import com.jd.blockchain.crypto.CryptoProvider;
import com.jd.blockchain.crypto.HashDigest;
import com.jd.blockchain.crypto.KeyGenUtils;
import com.jd.blockchain.crypto.PrivKey;
import com.jd.blockchain.crypto.PubKey;
import com.jd.blockchain.crypto.SignatureDigest;
import com.jd.blockchain.crypto.service.classic.ClassicCryptoService;
import com.jd.blockchain.crypto.service.sm.SMCryptoService;
import com.jd.blockchain.ledger.CryptoSetting;
import com.jd.blockchain.ledger.LedgerInitProperties;
import com.jd.blockchain.ledger.ParticipantNode;
import com.jd.blockchain.ledger.ParticipantNodeState;
import com.jd.blockchain.ledger.TransactionContent;
import com.jd.blockchain.ledger.core.CryptoConfig;
import com.jd.blockchain.ledger.core.LedgerInitDecision;
import com.jd.blockchain.ledger.core.LedgerInitProposal;
import com.jd.blockchain.ledger.core.LedgerManager;
import com.jd.blockchain.ledger.core.LedgerQuery;
import com.jd.blockchain.storage.service.DbConnectionFactory;
import com.jd.blockchain.tools.initializer.DBConnectionConfig;
import com.jd.blockchain.tools.initializer.LedgerInitProcess;
import com.jd.blockchain.tools.initializer.Prompter;
import com.jd.blockchain.tools.initializer.web.InitConsensusServiceFactory;
import com.jd.blockchain.tools.initializer.web.LedgerInitConfiguration;
import com.jd.blockchain.tools.initializer.web.LedgerInitConsensusService;
import com.jd.blockchain.tools.initializer.web.LedgerInitializeWebController;
import com.jd.blockchain.tools.initializer.web.ParticipantReplica;

import utils.Bytes;
import utils.concurrent.ThreadInvoker;
import utils.concurrent.ThreadInvoker.AsyncCallback;
import utils.io.FileUtils;
import utils.net.NetworkAddress;

public class Utils {

	public static final String PASSWORD = "abc";

	public static final String[] PUB_KEYS = { "7VeRLdGtSz1Y91gjLTqEdnkotzUfaAqdap3xw6fQ1yKHkvVq",
			"7VeRBsHM2nsGwP8b2ufRxz36hhNtSqjKTquzoa4WVKWty5sD",
			"7VeRAr3dSbi1xatq11ZcF7sEPkaMmtZhV9shonGJWk9T4pLe",
			"7VeRKoM5RE6iFXr214Hsiic2aoqCQ7MEU1dHQFRnjXQcReAS" };

	public static final String[] PRIV_KEYS = {
			"177gjzHTznYdPgWqZrH43W3yp37onm74wYXT4v9FukpCHBrhRysBBZh7Pzdo5AMRyQGJD7x",
			"177gju9p5zrNdHJVEQnEEKF4ZjDDYmAXyfG84V5RPGVc5xFfmtwnHA7j51nyNLUFffzz5UT",
			"177gjtwLgmSx5v1hFb46ijh7L9kdbKUpJYqdKVf9afiEmAuLgo8Rck9yu5UuUcHknWJuWaF",
			"177gk1pudweTq5zgJTh8y3ENCTwtSFsKyX7YnpuKPo7rKgCkCBXVXh5z2syaTCPEMbuWRns" };

	private static final String[] SUPPORTED_PROVIDERS = { ClassicCryptoService.class.getName(),
			SMCryptoService.class.getName() };

	private Map<NetworkAddress, LedgerInitConsensusService> serviceRegisterMap = new ConcurrentHashMap<>();

	public static LedgerInitProperties loadInitSetting() {
		ClassPathResource ledgerInitSettingResource = new ClassPathResource("ledger_init_test_web2.init");
		try (InputStream in = ledgerInitSettingResource.getInputStream()) {
			LedgerInitProperties setting = LedgerInitProperties.resolve(in);
			return setting;
		} catch (IOException e) {
			throw new IllegalStateException(e.getMessage(), e);
		}
	}

	public static Properties loadConsensusSetting() {
		ClassPathResource ledgerInitSettingResource = new ClassPathResource("bftsmart.config");
		try (InputStream in = ledgerInitSettingResource.getInputStream()) {
			return FileUtils.readProperties(in);
		} catch (IOException e) {
			throw new IllegalStateException(e.getMessage(), e);
		}
	}

	public static Replica[] loadParticipantNodes() {
		ParticipantNode[] participantNodes = new ParticipantNode[PUB_KEYS.length];
		for (int i = 0; i < PUB_KEYS.length; i++) {
			participantNodes[i] = new PartNode(i, KeyGenUtils.decodePubKey(PUB_KEYS[i]), ParticipantNodeState.CONSENSUS);
		}
		return ParticipantReplica.wrap(participantNodes);
	}
	
	public static boolean areEqual(TransactionContent initTxContent0, TransactionContent initTxContent1) {
		byte[] txBytes0 = BinaryProtocol.encode(initTxContent0, TransactionContent.class);
		byte[] txBytes1 = BinaryProtocol.encode(initTxContent1, TransactionContent.class);
		return Arrays.areEqual(txBytes0, txBytes1);
	}

	public static class NodeContext {

		private LedgerManager ledgerManager = new LedgerManager();

		private DbConnectionFactory dbConnFactory;

		private InitConsensusServiceFactory initCsServiceFactory;

		private LedgerInitProcess initProcess;

		private AsymmetricKeypair partiKey;

		public AsymmetricKeypair getPartiKey() {
			return partiKey;
		}

		public LedgerManager getLedgerManager() {
			return ledgerManager;
		}

		public DbConnectionFactory getStorageDb() {
			return dbConnFactory;
		}

		public NodeContext(NetworkAddress address, Map<NetworkAddress, LedgerInitConsensusService> serviceRegisterMap,
				DbConnectionFactory dbConnFactory) {
			this.dbConnFactory = dbConnFactory;
			this.initCsServiceFactory = new MultiThreadInterInvokerFactory(serviceRegisterMap);
			LedgerInitializeWebController initController = new LedgerInitializeWebController(dbConnFactory,
					initCsServiceFactory);
			serviceRegisterMap.put(address, initController);
			this.initProcess = initController;
		}

		public AsyncCallback<HashDigest> startInit(int currentId, PrivKey privKey, LedgerInitProperties setting,
				DBConnectionConfig dbConnConfig, Prompter prompter) {

			partiKey = new AsymmetricKeypair(setting.getConsensusParticipant(0).getPubKey(), privKey);

			ThreadInvoker<HashDigest> invoker = new ThreadInvoker<HashDigest>() {
				@Override
				protected HashDigest invoke() throws Exception {
					return initProcess.initialize(currentId, privKey, setting, dbConnConfig, prompter);
				}
			};

			return invoker.start();
		}

		public AsyncCallback<HashDigest> startInit(int currentId, PrivKey privKey, LedgerInitProperties setting,
				ConsensusViewSettings csProps, ConsensusProvider consensusProvider, DBConnectionConfig dbConnConfig,
				Prompter prompter, boolean autoVerifyHash) {
			CryptoAlgorithm algorithm = Crypto.getAlgorithm("SHA256");
			return startInit(currentId, privKey, setting, csProps, consensusProvider, dbConnConfig, prompter,
					autoVerifyHash, algorithm);
		}

		public AsyncCallback<HashDigest> startInit(int currentId, PrivKey privKey, LedgerInitProperties setting,
				ConsensusViewSettings csProps, ConsensusProvider consensusProvider, DBConnectionConfig dbConnConfig,
				Prompter prompter, boolean autoVerifyHash, CryptoAlgorithm hashAlg) {

			CryptoProvider[] supportedProviders = new CryptoProvider[SUPPORTED_PROVIDERS.length];
			for (int i = 0; i < SUPPORTED_PROVIDERS.length; i++) {
				supportedProviders[i] = Crypto.getProvider(SUPPORTED_PROVIDERS[i]);
			}
			CryptoConfig cryptoSetting = new CryptoConfig();
			cryptoSetting.setSupportedProviders(supportedProviders);
			cryptoSetting.setSupportedProviders(supportedProviders);
			cryptoSetting.setAutoVerifyHash(autoVerifyHash);
			cryptoSetting.setHashAlgorithm(hashAlg);

			return startInit(currentId, privKey, setting, csProps, consensusProvider, dbConnConfig, prompter,
					cryptoSetting);
		}

		public AsyncCallback<HashDigest> startInit(int currentId, PrivKey privKey, LedgerInitProperties setting,
				ConsensusViewSettings csProps, ConsensusProvider consensusProvider, DBConnectionConfig dbConnConfig,
				Prompter prompter, CryptoSetting cryptoSetting) {

			LedgerInitConfiguration ledgerInitConfig = LedgerInitConfiguration.create(setting);
			ledgerInitConfig.getLedgerSettings().setCryptoSetting(cryptoSetting);
			
			partiKey = new AsymmetricKeypair(setting.getConsensusParticipant(0).getPubKey(), privKey);

			ThreadInvoker<HashDigest> invoker = new ThreadInvoker<HashDigest>() {
				@Override
				protected HashDigest invoke() throws Exception {

					return initProcess.initialize(currentId, privKey, setting, dbConnConfig, prompter);
				}
			};

			return invoker.start();
		}

		public LedgerQuery registerLedger(HashDigest ledgerHash, DBConnectionConfig dbConnConf, LedgerDataStructure ledgerDataStructure) {
			return ledgerManager.register(ledgerHash, dbConnFactory.connect(dbConnConf.getUri()).getStorageService(), null, ledgerDataStructure);
		}
	}

	private static class MultiThreadInterInvokerFactory implements InitConsensusServiceFactory {

		private Map<NetworkAddress, LedgerInitConsensusService> nodeConsesusServices;

		public MultiThreadInterInvokerFactory(Map<NetworkAddress, LedgerInitConsensusService> nodeConsesusServices) {
			this.nodeConsesusServices = nodeConsesusServices;
		}

		@Override
		public LedgerInitConsensusService connect(NetworkAddress endpointAddress) {
			return new InitConsensusServiceProxy(nodeConsesusServices.get(endpointAddress));
		}

	}

	private static class InitConsensusServiceProxy implements LedgerInitConsensusService {

		private LedgerInitConsensusService initCsService;

		public InitConsensusServiceProxy(LedgerInitConsensusService initCsService) {
			this.initCsService = initCsService;
		}

		@Override
		public LedgerInitProposal requestPermission(int requesterId, SignatureDigest signature) {
			ThreadInvoker<LedgerInitProposal> invoker = new ThreadInvoker<LedgerInitProposal>() {
				@Override
				protected LedgerInitProposal invoke() {
					return initCsService.requestPermission(requesterId, signature);
				}
			};
			return invoker.startAndWait();
		}

		@Override
		public LedgerInitDecision synchronizeDecision(LedgerInitDecision initDecision) {
			ThreadInvoker<LedgerInitDecision> invoker = new ThreadInvoker<LedgerInitDecision>() {
				@Override
				protected LedgerInitDecision invoke() {
					return initCsService.synchronizeDecision(initDecision);
				}
			};
			return invoker.startAndWait();
		}

	}

	private static class PartNode implements ParticipantNode {

		private int id;

		private Bytes address;

		private String name;

		private PubKey pubKey;

		private ParticipantNodeState participantNodeState;

		public PartNode(int id, PubKey pubKey, ParticipantNodeState participantNodeState) {
			this(id, id + "", pubKey, participantNodeState);
		}

		public PartNode(int id, String name, PubKey pubKey, ParticipantNodeState participantNodeState) {
			this.id = id;
			this.name = name;
			this.pubKey = pubKey;
			this.address = AddressEncoding.generateAddress(pubKey);
			this.participantNodeState = participantNodeState;
		}

		@Override
		public int getId() {
			return id;
		}

		@Override
		public Bytes getAddress() {
			return address;
		}

		@Override
		public String getName() {
			return name;
		}

		@Override
		public PubKey getPubKey() {
			return pubKey;
		}

		@Override
		public ParticipantNodeState getParticipantNodeState() {
			return participantNodeState;
		}

	}

}
