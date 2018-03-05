package net.stargraph.core.features;

import net.stargraph.core.KnowledgeBase;
import net.stargraph.core.impl.corenlp.NERSearcher;
import net.stargraph.core.ner.NER;
import net.stargraph.core.search.index.EntityIndexSearcher;

public class NERFeature implements Feature<NER> {
    private NER ner;

    @Override
    public NER get(KnowledgeBase knowledgeBase) {
        if (ner == null) {
            ner = new NERSearcher(knowledgeBase.getSearcher(EntityIndexSearcher.class).getIndex());
        }
        return ner;
    }
}
