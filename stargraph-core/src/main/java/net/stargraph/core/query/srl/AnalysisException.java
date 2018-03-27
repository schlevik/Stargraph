package net.stargraph.core.query.srl;

public class AnalysisException extends Exception {

    public AnalysisException(Exception e) {
        super(e);
    }
    public AnalysisException(String msg) {
        super(msg);
    }
}
