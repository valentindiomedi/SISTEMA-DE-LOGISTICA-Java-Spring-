package com.backend.tpi.ms_solicitudes.config;

import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.id.IdentifierGenerator;

import java.io.Serializable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Generador de IDs personalizado que asigna el ID más bajo disponible (mayor a 0)
 * Reutiliza IDs liberados por registros eliminados
 */
public class LowestAvailableIdGenerator implements IdentifierGenerator {

    @Override
    public Serializable generate(SharedSessionContractImplementor session, Object object) {
        String tableName = getTableName(object);
        String idColumnName = getIdColumnName(object);
        
        Connection connection = null;
        try {
            connection = session.getJdbcConnectionAccess().obtainConnection();
        } catch (SQLException e) {
            throw new RuntimeException("Error al obtener conexión para generar ID", e);
        }
        
        try {
            // Buscar el ID más bajo disponible mayor a 0
            String sql = String.format(
                "SELECT COALESCE(MIN(t1.%s + 1), 1) AS next_id " +
                "FROM %s t1 " +
                "WHERE NOT EXISTS (SELECT 1 FROM %s t2 WHERE t2.%s = t1.%s + 1) " +
                "AND t1.%s >= 0",
                idColumnName, tableName, tableName, idColumnName, idColumnName, idColumnName
            );
            
            try (PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet resultSet = statement.executeQuery()) {
                
                if (resultSet.next()) {
                    long nextId = resultSet.getLong("next_id");
                    // Asegurar que el ID sea al menos 1
                    return nextId > 0 ? nextId : 1L;
                }
            }
            
            // Si la tabla está vacía, retornar 1
            return 1L;
            
        } catch (SQLException e) {
            throw new RuntimeException("Error al generar ID para " + tableName, e);
        } finally {
            try {
                session.getJdbcConnectionAccess().releaseConnection(connection);
            } catch (SQLException e) {
                // Log error but don't throw
            }
        }
    }
    
    private String getTableName(Object object) {
        String className = object.getClass().getSimpleName();
        switch (className) {
            case "Cliente":
                return "clientes";
            case "Solicitud":
                return "solicitudes";
            case "Contenedor":
                return "contenedores";
            default:
                throw new IllegalArgumentException("Tipo de entidad no soportado: " + className);
        }
    }
    
    private String getIdColumnName(Object object) {
        String className = object.getClass().getSimpleName();
        switch (className) {
            case "Cliente":
                return "id_cliente";
            case "Solicitud":
                return "id_solicitud";
            case "Contenedor":
                return "id_contenedor";
            default:
                throw new IllegalArgumentException("Tipo de entidad no soportado: " + className);
        }
    }
}
