package fixtures.object;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import java.util.ArrayList;
import java.util.List;


@Path("/objects")
public class ObjectResource {	
	@GET
	public Object get() {
		return new Object();
	}

    @GET
    public List<Object> getAll() {
        return new ArrayList<Object>();
    }
}
