package org.keycloak.example.photoz.album;

import org.keycloak.authorization.client.AuthzClient;
import org.keycloak.authorization.client.representation.ResourceRepresentation;
import org.keycloak.authorization.client.representation.ScopeRepresentation;
import org.keycloak.authorization.client.resource.ProtectionResource;
import org.keycloak.example.photoz.ErrorResponse;
import org.keycloak.example.photoz.entity.Album;

import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.security.Principal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Path("/album")
@Stateless
public class AlbumService {

    public static final String SCOPE_ALBUM_VIEW = "urn:photoz.com:scopes:album:view";
    public static final String SCOPE_ALBUM_CREATE = "urn:photoz.com:scopes:album:create";
    public static final String SCOPE_ALBUM_DELETE = "urn:photoz.com:scopes:album:delete";

    @PersistenceContext
    private EntityManager entityManager;

    @POST
    @Consumes("application/json")
    public Response create(@Context HttpServletRequest request, Album newAlbum) {
        Principal userPrincipal = request.getUserPrincipal();

        newAlbum.setUserId(userPrincipal.getName());

        Query queryDuplicatedAlbum = this.entityManager.createQuery("from Album where name = :name and userId = :userId");

        queryDuplicatedAlbum.setParameter("name", newAlbum.getName());
        queryDuplicatedAlbum.setParameter("userId", userPrincipal.getName());

        if (!queryDuplicatedAlbum.getResultList().isEmpty()) {
            throw new ErrorResponse("Name [" + newAlbum.getName() + "] already taken. Choose another one.", Status.CONFLICT);
        }

        this.entityManager.persist(newAlbum);

        createProtectedResource(newAlbum);

        return Response.ok(newAlbum).build();
    }

    @Path("{id}")
    @DELETE
    public Response delete(@PathParam("id") String id) {
        Album album = this.entityManager.find(Album.class, Long.valueOf(id));

        try {
            deleteProtectedResource(album);
            this.entityManager.remove(album);
        } catch (Exception e) {
            throw new RuntimeException("Could not delete album.", e);
        }

        return Response.ok().build();
    }

    @GET
    @Produces("application/json")
    public Response findAll(@Context HttpServletRequest request) {
        return Response.ok(this.entityManager.createQuery("from Album where userId = '" + request.getUserPrincipal().getName() + "'").getResultList()).build();
    }

    @GET
    @Path("{id}")
    @Produces("application/json")
    public Response findById(@PathParam("id") String id) {
        List result = this.entityManager.createQuery("from Album where id = " + id).getResultList();

        if (result.isEmpty()) {
            return Response.status(Status.NOT_FOUND).build();
        }

        return Response.ok(result.get(0)).build();
    }

    private void createProtectedResource(Album album) {
        try {
            HashSet<ScopeRepresentation> scopes = new HashSet<>();

            scopes.add(new ScopeRepresentation(SCOPE_ALBUM_VIEW));
            scopes.add(new ScopeRepresentation(SCOPE_ALBUM_CREATE));
            scopes.add(new ScopeRepresentation(SCOPE_ALBUM_DELETE));

            ResourceRepresentation albumResource = new ResourceRepresentation(album.getName(), scopes, "/album/" + album.getId(), "http://photoz.com/album");

            albumResource.setOwner(album.getUserId());

            AuthzClient.create().protection().resource().create(albumResource);
        } catch (Exception e) {
            throw new RuntimeException("Could not register protected resource.", e);
        }
    }

    private void deleteProtectedResource(Album album) {
        String uri = "/album/" + album.getId();

        try {
            ProtectionResource protection = AuthzClient.create().protection();
            Set<String> search = protection.resource().findByFilter("uri=" + uri);

            if (search.isEmpty()) {
                throw new RuntimeException("Could not find protected resource with URI [" + uri + "]");
            }

            protection.resource().delete(search.iterator().next());
        } catch (Exception e) {
            throw new RuntimeException("Could not search protected resource.", e);
        }
    }
}
