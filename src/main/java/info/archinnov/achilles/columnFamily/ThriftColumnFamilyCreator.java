package info.archinnov.achilles.columnFamily;

import static info.archinnov.achilles.dao.CounterDao.COUNTER_CF;
import info.archinnov.achilles.entity.context.ConfigurationContext;
import info.archinnov.achilles.entity.metadata.EntityMeta;
import info.archinnov.achilles.entity.metadata.PropertyMeta;
import info.archinnov.achilles.exception.AchillesInvalidColumnFamilyException;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Pattern;

import me.prettyprint.hector.api.Cluster;
import me.prettyprint.hector.api.Keyspace;
import me.prettyprint.hector.api.ddl.ColumnFamilyDefinition;
import me.prettyprint.hector.api.ddl.KeyspaceDefinition;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ColumnFamilyHelper
 * 
 * @author DuyHai DOAN
 * 
 */
public class ThriftColumnFamilyCreator
{
	private static final Logger log = LoggerFactory.getLogger(ThriftColumnFamilyCreator.class);
	private Cluster cluster;
	private Keyspace keyspace;
	private ThriftColumnFamilyHelper thriftColumnFamilyHelper = new ThriftColumnFamilyHelper();
	private List<ColumnFamilyDefinition> cfDefs;
	public static final Pattern CF_PATTERN = Pattern.compile("[a-zA-Z0-9_]{1,48}");
	private Set<String> columnFamilyNames = new HashSet<String>();

	public ThriftColumnFamilyCreator(Cluster cluster, Keyspace keyspace) {
		this.cluster = cluster;
		this.keyspace = keyspace;
		KeyspaceDefinition keyspaceDef = this.cluster.describeKeyspace(this.keyspace
				.getKeyspaceName());
		if (keyspaceDef != null && keyspaceDef.getCfDefs() != null)
		{
			cfDefs = keyspaceDef.getCfDefs();
		}
	}

	public ColumnFamilyDefinition discoverColumnFamily(String columnFamilyName)
	{
		log.debug("Start discovery of column family {}", columnFamilyName);
		for (ColumnFamilyDefinition cfDef : this.cfDefs)
		{
			if (StringUtils.equals(cfDef.getName(), columnFamilyName))
			{
				log.debug("Existing column family {} found", columnFamilyName);
				return cfDef;
			}
		}
		return null;
	}

	public void addColumnFamily(ColumnFamilyDefinition cfDef)
	{
		if (!columnFamilyNames.contains(cfDef.getName()))
		{
			columnFamilyNames.add(cfDef.getName());
			cluster.addColumnFamily(cfDef, true);
		}

	}

	public void createColumnFamily(EntityMeta<?> entityMeta)
	{
		log.debug("Creating column family for entityMeta {}", entityMeta.getClassName());
		String columnFamilyName = entityMeta.getColumnFamilyName();
		if (!columnFamilyNames.contains(columnFamilyName))
		{
			ColumnFamilyDefinition cfDef;
			if (entityMeta.isWideRow())
			{

				PropertyMeta<?, ?> propertyMeta = entityMeta.getPropertyMetas().values().iterator()
						.next();
				cfDef = thriftColumnFamilyHelper.buildWideRowCF(keyspace.getKeyspaceName(),
						propertyMeta, entityMeta.getIdMeta().getValueClass(), columnFamilyName,
						entityMeta.getClassName());
			}
			else
			{
				cfDef = this.thriftColumnFamilyHelper.buildEntityCF(entityMeta,
						this.keyspace.getKeyspaceName());

			}
			this.addColumnFamily(cfDef);
		}

	}

	public void validateOrCreateColumnFamilies(Map<Class<?>, EntityMeta<?>> entityMetaMap,
			ConfigurationContext configContext, boolean hasCounter)
	{
		for (Entry<Class<?>, EntityMeta<?>> entry : entityMetaMap.entrySet())
		{

			EntityMeta<?> entityMeta = entry.getValue();
			for (Entry<String, PropertyMeta<?, ?>> entryMeta : entityMeta.getPropertyMetas()
					.entrySet())
			{
				PropertyMeta<?, ?> propertyMeta = entryMeta.getValue();

				if (propertyMeta.type().isWideMap())
				{
					validateOrCreateCFForWideMap(propertyMeta, entityMeta.getIdMeta()
							.getValueClass(), configContext.isForceColumnFamilyCreation(),
							propertyMeta.getExternalCFName(), entityMeta.getClassName());
				}
			}

			this.validateOrCreateCFForEntity(entityMeta,
					configContext.isForceColumnFamilyCreation());
		}

		if (hasCounter)
		{
			this.validateOrCreateCFForCounter(configContext.isForceColumnFamilyCreation());
		}
	}

	private <ID> void validateOrCreateCFForWideMap(PropertyMeta<?, ?> propertyMeta,
			Class<ID> keyClass, boolean forceColumnFamilyCreation, String externalColumnFamilyName,
			String entityName)
	{

		ColumnFamilyDefinition cfDef = discoverColumnFamily(externalColumnFamilyName);
		if (cfDef == null)
		{
			if (forceColumnFamilyCreation)
			{
				log.debug("Force creation of column family for propertyMeta {}",
						propertyMeta.getPropertyName());

				cfDef = thriftColumnFamilyHelper.buildWideRowCF(keyspace.getKeyspaceName(),
						propertyMeta, keyClass, externalColumnFamilyName, entityName);
				this.addColumnFamily(cfDef);
			}
			else
			{
				throw new AchillesInvalidColumnFamilyException("The required column family '"
						+ externalColumnFamilyName + "' does not exist for field '"
						+ propertyMeta.getPropertyName() + "'");
			}
		}
		else
		{
			this.thriftColumnFamilyHelper.validateCFWithPropertyMeta(cfDef, propertyMeta,
					externalColumnFamilyName);
		}
	}

	public void validateOrCreateCFForEntity(EntityMeta<?> entityMeta,
			boolean forceColumnFamilyCreation)
	{
		ColumnFamilyDefinition cfDef = this.discoverColumnFamily(entityMeta.getColumnFamilyName());
		if (cfDef == null)
		{
			if (forceColumnFamilyCreation)
			{
				log.debug("Force creation of column family for entityMeta {}",
						entityMeta.getClassName());

				this.createColumnFamily(entityMeta);
			}
			else
			{
				throw new AchillesInvalidColumnFamilyException("The required column family '"
						+ entityMeta.getColumnFamilyName() + "' does not exist for entity '"
						+ entityMeta.getClassName() + "'");
			}
		}
		else
		{
			this.thriftColumnFamilyHelper.validateCFWithEntityMeta(cfDef, entityMeta);
		}
	}

	private void validateOrCreateCFForCounter(boolean forceColumnFamilyCreation)
	{
		ColumnFamilyDefinition cfDef = this.discoverColumnFamily(COUNTER_CF);
		if (cfDef == null)
		{
			if (forceColumnFamilyCreation)
			{
				log.debug("Force creation of column family for counters");

				this.createCounterColumnFamily();
			}
			else
			{
				throw new AchillesInvalidColumnFamilyException("The required column family '"
						+ COUNTER_CF + "' does not exist");
			}
		}
		else
		{
			this.thriftColumnFamilyHelper.validateCounterCF(cfDef);
		}

	}

	private void createCounterColumnFamily()
	{
		log.debug("Creating generic counter column family");
		if (!columnFamilyNames.contains(COUNTER_CF))
		{
			ColumnFamilyDefinition cfDef = thriftColumnFamilyHelper.buildCounterCF(this.keyspace
					.getKeyspaceName());
			this.addColumnFamily(cfDef);
		}
	}
}