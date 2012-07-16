package aQute.bnd.build.model.clauses;

import org.osgi.framework.*;

import aQute.bnd.header.*;

public class ExportedPackage extends HeaderClause {

	public ExportedPackage(String packageName, Attrs attribs) {
		super(packageName, attribs);
	}

	@Override
	protected boolean newlinesBetweenAttributes() {
		return false;
	}

	public void setVersionString(String version) {
		attribs.put(Constants.VERSION_ATTRIBUTE, version);
	}

	public String getVersionString() {
		return attribs.get(Constants.VERSION_ATTRIBUTE);
	}

	public boolean isProvided() {
		return Boolean.valueOf(attribs.get(aQute.bnd.osgi.Constants.PROVIDE_DIRECTIVE));
	}

	public void setProvided(boolean provided) {
		if (provided)
			attribs.put(aQute.bnd.osgi.Constants.PROVIDE_DIRECTIVE, Boolean.toString(true));
		else
			attribs.remove(aQute.bnd.osgi.Constants.PROVIDE_DIRECTIVE);
	}

	@Override
	public ExportedPackage clone() {
		return new ExportedPackage(this.name, new Attrs(this.attribs));
	}
}
