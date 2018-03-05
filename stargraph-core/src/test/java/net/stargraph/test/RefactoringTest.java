package net.stargraph.test;

import com.typesafe.config.ConfigFactory;
import net.stargraph.core.Stargraph;
import org.testng.annotations.Test;

public class RefactoringTest {
    private String kbName = "lucene-obama";

    @Test
    public void test() {
        ConfigFactory.invalidateCaches();
        Stargraph stargraph = new Stargraph(ConfigFactory.load().getConfig("stargraph"), false);
        stargraph.setKBInitSet(kbName);
        stargraph.initialize();
        System.out.println(stargraph.getKBCore(kbName).toString());
        stargraph.getKBCore(kbName).getIndexIDs().stream().map(stargraph::getIndex).forEach(System.out::println);
    }
}
