package sv.edu.utec.datos;
import sv.edu.utec.modelo.Producto;
import sv.edu.utec.modelo.ResumenCategoria;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
/**
 * Implementacion del contrato con PostgreSQL (Supabase) via JDBC.
 * Es la UNICA clase del proyecto que contiene SQL. Solo manipula datos
 * (DML): la tabla la crea el administrador con sql/01_esquema.sql.
 */
public class JdbcProductoRepositorio implements ProductoRepositorio {
    // Codigos SQLState estandar de PostgreSQL
    private static final String DUPLICADO = "23505"; // unique_violation
    private static final String REGLA_VIOLADA = "23514"; // check_violation
    private static final String COLUMNAS =
            "codigo, nombre, categoria, precio, existencia";
// ... aqui quedan los 11 metodos que genero IntelliJ ...
    /** Ejecuta un SELECT con parametros y convierte cada fila en Producto. */
    private List<Producto> consultar(String sql, Object... parametros) {
        List<Producto> resultado = new ArrayList<>();
        try (Connection cn = ConexionPostgres.obtener();
             PreparedStatement ps = cn.prepareStatement(sql)) {
            for (int i = 0; i < parametros.length; i++) {
                ps.setObject(i + 1, parametros[i]); // los indices empiezan en 1
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    resultado.add(aProducto(rs));
                }
            }
            return resultado;
        } catch (SQLException e) {
            throw new RepositorioException("Error al consultar productos", e);
        }
    }
    private void asignarParametros(PreparedStatement ps, Producto p) throws SQLException {
        ps.setString(1, p.getCodigo());
        ps.setString(2, p.getNombre());
        ps.setString(3, p.getCategoria());
        ps.setDouble(4, p.getPrecio());
        ps.setInt(5, p.getExistencia());
    }
    private Producto aProducto(ResultSet rs) throws SQLException {
        return new Producto(
                rs.getString("codigo"),
                rs.getString("nombre"),
                rs.getString("categoria"),
                rs.getDouble("precio"),
                rs.getInt("existencia"));
    }
    /** Traduce el SQLState de PostgreSQL al idioma del negocio. */
    private RepositorioException traducir(SQLException e, String codigo) {
        String estado = e.getSQLState();
        if (DUPLICADO.equals(estado)) {
            return new RepositorioException(
                    "Ya existe un producto con el codigo " + codigo, e);
        }
        if (REGLA_VIOLADA.equals(estado)) {
            return new RepositorioException(
                    "PostgreSQL rechazo un valor fuera de regla en " + codigo, e);
        }
        return new RepositorioException(
                "Error de base de datos [" + estado + "]: " + e.getMessage(), e);
    }

    // ------------------------------------------------------------------
// C: Create
// ------------------------------------------------------------------
    @Override
    public void insertar(Producto p) {
        String sql = "INSERT INTO producto (" + COLUMNAS + ") VALUES (?, ?, ?, ?, ?)";
        try (Connection cn = ConexionPostgres.obtener();
             PreparedStatement ps = cn.prepareStatement(sql)) {
            asignarParametros(ps, p);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw traducir(e, p.getCodigo());
        }
    }


    @Override
    public int insertarVarios(List<Producto> productos) {

        int contador = 0;

        for (Producto p : productos) {
            insertar(p);
            contador++;
        }

        return contador;
    }

    // ------------------------------------------------------------------
// R: Read
// ------------------------------------------------------------------
    @Override
    public Optional<Producto> buscarPorCodigo(String codigo) {
        List<Producto> r = consultar(
                "SELECT " + COLUMNAS + " FROM producto WHERE codigo = ?", codigo);
        return r.stream().findFirst();
    }
    @Override
    public List<Producto> listarTodos() {
        return consultar("SELECT " + COLUMNAS + " FROM producto ORDER BY codigo");
    }

    @Override
    public List<Producto> listarPorCategoria(String categoria) {
        return consultar("SELECT " + COLUMNAS
                + " FROM producto WHERE categoria = ? ORDER BY nombre", categoria);
    }


    @Override
    public List<Producto> buscarPorNombre(String texto) {
// Se escapan los comodines de LIKE: lo que escribe el usuario
// se busca literal, igual que Pattern.quote en la Guia 4.
        String literal = texto.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return consultar("SELECT " + COLUMNAS
                        + " FROM producto WHERE nombre ILIKE ? ESCAPE '\\' ORDER BY nombre",
                "%" + literal + "%");
    }


    @Override
    public List<Producto> buscarPorRangoDePrecio(double minimo, double maximo) {
        return consultar("SELECT " + COLUMNAS
                        + " FROM producto WHERE precio BETWEEN ? AND ? ORDER BY precio",
                minimo, maximo);
    }


    // ------------------------------------------------------------------
// U: Update
// ------------------------------------------------------------------
    @Override
    public boolean actualizar(Producto p) {
        String sql = """
UPDATE producto
SET nombre = ?, categoria = ?, precio = ?, existencia = ?,
actualizado_en = now()
WHERE codigo = ?""";
        try (Connection cn = ConexionPostgres.obtener();
             PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setString(1, p.getNombre());
            ps.setString(2, p.getCategoria());
            ps.setDouble(3, p.getPrecio());
            ps.setInt(4, p.getExistencia());
            ps.setString(5, p.getCodigo());
            return ps.executeUpdate() > 0; // 0 filas: el codigo no existe
        } catch (SQLException e) {
            throw traducir(e, p.getCodigo());
        }
    }
    @Override
    public boolean ajustarExistencia(String codigo, int cantidad) {
        if (cantidad == 0) {
            throw new IllegalArgumentException("La cantidad no puede ser cero");
        }
// Revisar y sumar/restar en UNA sentencia atomica dentro del servidor
        String sql = """
UPDATE producto
SET existencia = existencia + ?, actualizado_en = now()
WHERE codigo = ? AND existencia + ? >= 0""";
        try (Connection cn = ConexionPostgres.obtener();
             PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setInt(1, cantidad);
            ps.setString(2, codigo);
            ps.setInt(3, cantidad);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw traducir(e, codigo);
        }
    }

    // ------------------------------------------------------------------
// D: Delete
// ------------------------------------------------------------------
    @Override
    public boolean eliminar(String codigo) {
        try (Connection cn = ConexionPostgres.obtener();
             PreparedStatement ps = cn.prepareStatement(
                     "DELETE FROM producto WHERE codigo = ?")) {
            ps.setString(1, codigo);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw traducir(e, codigo);
        }
    }
    @Override
    public List<ResumenCategoria> resumenPorCategoria() {
        return List.of();
    }
}
