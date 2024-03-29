package test.perf.com.jd.blockchain.consensus.node;

import bftsmart.reconfiguration.util.HostsConfig;
import com.jd.blockchain.consensus.AsyncActionResponse;
import com.jd.blockchain.consensus.bftsmart.service.BftsmartNodeServer;
import com.jd.blockchain.consensus.bftsmart.service.BftsmartServerSettingConfig;
import com.jd.blockchain.peer.consensus.ConsensusMessageDispatcher;
import utils.io.MemoryStorage;

import java.util.Properties;

public class TestReplica extends BftsmartNodeServer {

	private byte[] retnOK = { 1 };

	public TestReplica(int id, Properties systemsConfig, HostsConfig hostConfig) {
		super(new BftsmartServerSettingConfig(), new ConsensusMessageDispatcher(), null,
				new MemoryStorage("TestReplica"));
	}

//	@Override
//	protected AsyncActionResponse execute(ActionRequest request) {
//		ConsoleUtils.info("Receive request ...");
//		return new SimpleResponse(retnOK);
//	}

	private static class SimpleResponse implements AsyncActionResponse {

		private byte[] data;

		public SimpleResponse(byte[] data) {
			this.data = data;
		}

		@Override
		public byte[] process() {
			return data;
		}

	}

	@Override
	public void installSnapshot(byte[] state) {
		// TODO Auto-generated method stub

	}

	@Override
	public byte[] getBlockHashByCid(int cid) {
		// TODO Auto-generated method stub
		return null;
	}
}
