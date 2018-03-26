package net.stargraph.core.search.index;


import net.stargraph.core.Index;

public class NoIndexSearcher extends BaseIndexSearcher implements IndexSearcher {


    public NoIndexSearcher(Index index) {
        super(index);
    }

    @Override
    public Index getIndex() {
        return null;
    }
}
